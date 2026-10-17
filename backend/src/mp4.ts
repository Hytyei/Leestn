// Reads the audio track of an M4A/MP4 file and cuts it into pieces at AAC frame boundaries.
// Each piece is rewritten as a plain "ADTS" AAC stream, which can be decoded on its own,
// so a speech model never sees a broken container. Only AAC audio is supported.

export class Unsupported extends Error {}

export interface AacInfo {
  /** ADTS profile field: audio object type minus one (1 = AAC-LC). */
  profile: number;
  /** ADTS sampling frequency index. */
  sfi: number;
  channels: number;
}

/** Consecutive audio frames stored back to back in the file: where they start and how long each one is. */
export type Run = [offset: number, sizes: number[]];

export interface Slice {
  /** Seconds from the start of the episode to the first frame of this slice. */
  t0: number;
  runs: Run[];
}

export interface Plan {
  aac: AacInfo;
  slices: Slice[];
  seconds: number;
}

const u16 = (d: DataView, o: number) => d.getUint16(o);
const u32 = (d: DataView, o: number) => d.getUint32(o);
const tag = (d: DataView, o: number) => String.fromCharCode(d.getUint8(o), d.getUint8(o + 1), d.getUint8(o + 2), d.getUint8(o + 3));

interface Box {
  type: string;
  body: number;
  end: number;
}

function* boxes(d: DataView, start: number, end: number): Generator<Box> {
  let p = start;
  while (p + 8 <= end) {
    let size = u32(d, p);
    const type = tag(d, p + 4);
    let head = 8;
    if (size === 1) {
      if (p + 16 > end) return;
      size = u32(d, p + 8) * 2 ** 32 + u32(d, p + 12);
      head = 16;
    } else if (size === 0) {
      size = end - p;
    }
    if (size < head || p + size > end) return;
    yield { type, body: p + head, end: p + size };
    p += size;
  }
}

function child(d: DataView, parent: Box | { body: number; end: number }, type: string): Box | null {
  for (const b of boxes(d, parent.body, parent.end)) if (b.type === type) return b;
  return null;
}

const FREQS = [96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350];

/** Reads the AudioSpecificConfig bits of an AAC stream. */
function parseAsc(a: Uint8Array): AacInfo {
  let bit = 0;
  const read = (n: number) => {
    let v = 0;
    for (let i = 0; i < n; i++) {
      const byte = a[bit >> 3] ?? 0;
      v = v * 2 + ((byte >> (7 - (bit & 7))) & 1);
      bit++;
    }
    return v;
  };
  let type = read(5);
  if (type === 31) type = 32 + read(6);
  let sfi = read(4);
  if (sfi === 15) {
    const hz = read(24);
    const idx = FREQS.indexOf(hz);
    if (idx < 0) throw new Unsupported("This AAC file uses an unusual sample rate.");
    sfi = idx;
  }
  const channels = read(4);
  // HE-AAC announces the SBR layer first. The core stream is plain AAC-LC at the (lower) first rate.
  if (type === 5 || type === 29) type = 2;
  if (type < 1 || type > 4) throw new Unsupported("This audio is not AAC-LC, so it can't be transcribed yet.");
  if (sfi > 12) throw new Unsupported("This AAC file uses an unusual sample rate.");
  if (channels < 1 || channels > 7) throw new Unsupported("This AAC file has an unusual channel layout.");
  return { profile: type - 1, sfi, channels };
}

/** MPEG-4 descriptors: a tag byte, a 1 to 4 byte length, then the body. */
function descriptor(d: DataView, p: number, end: number): { tag: number; body: number; end: number } | null {
  if (p + 2 > end) return null;
  const t = d.getUint8(p++);
  let len = 0;
  for (let i = 0; i < 4 && p < end; i++) {
    const b = d.getUint8(p++);
    len = (len << 7) | (b & 0x7f);
    if (!(b & 0x80)) break;
  }
  return { tag: t, body: p, end: Math.min(end, p + len) };
}

function parseEsds(d: DataView, esds: Box): AacInfo {
  const es = descriptor(d, esds.body + 4, esds.end); // skip version and flags
  if (!es || es.tag !== 0x03) throw new Unsupported("Couldn't read the audio settings of this file.");
  let p = es.body + 2; // ES_ID
  const flags = d.getUint8(p++);
  if (flags & 0x80) p += 2;
  if (flags & 0x40) p += 1 + d.getUint8(p);
  if (flags & 0x20) p += 2;
  const cfg = descriptor(d, p, es.end);
  if (!cfg || cfg.tag !== 0x04) throw new Unsupported("Couldn't read the audio settings of this file.");
  const objectType = d.getUint8(cfg.body);
  if (objectType !== 0x40 && objectType !== 0x66 && objectType !== 0x67 && objectType !== 0x68) {
    throw new Unsupported("This audio isn't AAC, so it can't be transcribed yet.");
  }
  const dsi = descriptor(d, cfg.body + 13, cfg.end);
  if (!dsi || dsi.tag !== 0x05) throw new Unsupported("Couldn't read the audio settings of this file.");
  return parseAsc(new Uint8Array(d.buffer, d.byteOffset + dsi.body, dsi.end - dsi.body));
}

/**
 * Where the `moov` box (the index of the file) is. It may come first or last, so we hop over the top-level
 * boxes, reading only their 16 byte headers. [read] fetches a byte range of the file.
 */
export async function findMoov(size: number, read: (from: number, to: number) => Promise<Uint8Array>, maxBytes = 12 * 1024 * 1024): Promise<{ from: number; to: number }> {
  let p = 0;
  for (let hops = 0; hops < 24 && p + 8 <= size; hops++) {
    const h = await read(p, Math.min(size, p + 16));
    if (h.length < 8) break;
    const d = new DataView(h.buffer, h.byteOffset, h.length);
    let len = u32(d, 0);
    const type = tag(d, 4);
    if (len === 1) {
      if (h.length < 16) break;
      len = u32(d, 8) * 2 ** 32 + u32(d, 12);
    } else if (len === 0) {
      len = size - p;
    }
    if (len < 8) break;
    if (type === "moov") {
      if (len > maxBytes) throw new Unsupported("This file's index is unusually large.");
      return { from: p, to: Math.min(size, p + len) };
    }
    p += len;
  }
  throw new Unsupported("Couldn't find the audio index in this file.");
}

/** Reads a whole `moov` box and cuts the audio into pieces of about [targetBytes] (including the 7 byte ADTS headers). */
export function plan(moovBytes: Uint8Array, targetBytes: number, maxGroupsPerSlice = 12): Plan {
  const d = new DataView(moovBytes.buffer, moovBytes.byteOffset, moovBytes.length);
  const moov = child(d, { body: 0, end: d.byteLength }, "moov");
  if (!moov) throw new Unsupported("Couldn't find the audio index in this file.");

  let audio: Box | null = null;
  for (const trak of boxes(d, moov.body, moov.end)) {
    if (trak.type !== "trak") continue;
    const mdia = child(d, trak, "mdia");
    const hdlr = mdia && child(d, mdia, "hdlr");
    if (mdia && hdlr && tag(d, hdlr.body + 8) === "soun") {
      audio = mdia;
      break;
    }
  }
  if (!audio) throw new Unsupported("This file has no audio track.");

  const mdhd = child(d, audio, "mdhd");
  const minf = child(d, audio, "minf");
  const stbl = minf && child(d, minf, "stbl");
  if (!mdhd || !stbl) throw new Unsupported("Couldn't read the audio index of this file.");
  const timescale = d.getUint8(mdhd.body) === 1 ? u32(d, mdhd.body + 20) : u32(d, mdhd.body + 12);
  if (!timescale) throw new Unsupported("Couldn't read the audio timing of this file.");

  // sample description: must be mp4a with an esds box
  const stsd = child(d, stbl, "stsd");
  if (!stsd) throw new Unsupported("Couldn't read the audio format of this file.");
  const entry = stsd.body + 8;
  const entryType = tag(d, entry + 4);
  if (entryType !== "mp4a") throw new Unsupported("This audio isn't AAC, so it can't be transcribed yet.");
  const version = u16(d, entry + 8 + 8);
  const kids = entry + 8 + 28 + (version === 1 ? 16 : version === 2 ? 36 : 0);
  const esds = child(d, { body: kids, end: u32(d, entry) + entry }, "esds");
  if (!esds) throw new Unsupported("Couldn't read the audio settings of this file.");
  const aac = parseEsds(d, esds);

  // sizes
  const stsz = child(d, stbl, "stsz");
  const stsc = child(d, stbl, "stsc");
  const stts = child(d, stbl, "stts");
  const co = child(d, stbl, "stco") ?? child(d, stbl, "co64");
  if (!stsz || !stsc || !stts || !co) throw new Unsupported("Couldn't read the audio index of this file.");
  const wide = co.type === "co64";
  const constSize = u32(d, stsz.body + 4);
  const nSamples = u32(d, stsz.body + 8);
  if (nSamples === 0 || nSamples > 3_000_000) throw new Unsupported("This file has an unexpected number of audio frames.");
  const sizeOf = (i: number) => (constSize ? constSize : u32(d, stsz.body + 12 + i * 4));

  const nChunks = u32(d, co.body + 4);
  const chunkOffset = (c: number) => (wide ? u32(d, co.body + 8 + c * 8) * 2 ** 32 + u32(d, co.body + 8 + c * 8 + 4) : u32(d, co.body + 8 + c * 4));
  const nStsc = u32(d, stsc.body + 4);
  const nStts = u32(d, stts.body + 4);

  const slices: Slice[] = [];
  let runs: Run[] = [];
  let cur: Run | null = null;
  let bytes = 0;
  let groups = 0;
  let sample = 0;
  let time = 0; // in timescale units
  let sttsIdx = 0;
  let sttsLeft = nStts ? u32(d, stts.body + 8) : 0;
  let delta = nStts ? u32(d, stts.body + 12) : 0;
  let lastEnd = -1;
  let sliceFirstTime = 0;

  const close = () => {
    if (cur) runs.push(cur);
    if (runs.length) slices.push({ t0: Math.round((sliceFirstTime / timescale) * 1000) / 1000, runs });
    runs = [];
    cur = null;
    bytes = 0;
    groups = 0;
    lastEnd = -1;
  };

  let stscIdx = 0;
  for (let c = 0; c < nChunks && sample < nSamples; c++) {
    while (stscIdx + 1 < nStsc && u32(d, stsc.body + 8 + (stscIdx + 1) * 12) <= c + 1) stscIdx++;
    const perChunk = u32(d, stsc.body + 8 + stscIdx * 12 + 4);
    let off = chunkOffset(c);
    for (let k = 0; k < perChunk && sample < nSamples; k++, sample++) {
      const sz = sizeOf(sample);
      if (!bytes) sliceFirstTime = time;
      if (cur && lastEnd === off) {
        cur[1].push(sz);
      } else {
        if (cur) runs.push(cur);
        if (lastEnd >= 0 && off - lastEnd > 262_144) groups++;
        if (groups >= maxGroupsPerSlice) throw new Unsupported("This file mixes video with the audio, so it can't be transcribed yet.");
        cur = [off, [sz]];
      }
      lastEnd = off + sz;
      off += sz;
      bytes += sz + 7;
      // advance the clock past this frame
      time += delta;
      if (--sttsLeft <= 0 && sttsIdx + 1 < nStts) {
        sttsIdx++;
        sttsLeft = u32(d, stts.body + 8 + sttsIdx * 8);
        delta = u32(d, stts.body + 8 + sttsIdx * 8 + 4);
      }
      if (bytes >= targetBytes) close();
    }
  }
  close();
  if (!slices.length) throw new Unsupported("This file has no audio frames.");
  return { aac, slices, seconds: time / timescale };
}

/** Writes the 7 byte header that makes one raw AAC frame a self-contained ADTS frame. */
function adtsHeader(out: Uint8Array, at: number, frameLen: number, a: AacInfo) {
  const len = frameLen + 7;
  out[at] = 0xff;
  out[at + 1] = 0xf1; // MPEG-4, no CRC
  out[at + 2] = (a.profile << 6) | (a.sfi << 2) | ((a.channels >> 2) & 1);
  out[at + 3] = ((a.channels & 3) << 6) | ((len >> 11) & 3);
  out[at + 4] = (len >> 3) & 0xff;
  out[at + 5] = ((len & 7) << 5) | 0x1f;
  out[at + 6] = 0xfc;
}

/**
 * Fetches the frames of one slice and returns them as an ADTS stream.
 * [read] fetches a byte range of the file (end exclusive). Nearby runs share one request.
 */
export async function buildAdts(slice: Slice, aac: AacInfo, read: (from: number, to: number) => Promise<Uint8Array>): Promise<Uint8Array> {
  let total = 0;
  for (const [, sizes] of slice.runs) for (const s of sizes) total += s + 7;
  const out = new Uint8Array(total);
  let w = 0;

  let i = 0;
  while (i < slice.runs.length) {
    // group runs that are close together into one request
    let j = i;
    let spanEnd = slice.runs[i][0] + slice.runs[i][1].reduce((a, b) => a + b, 0);
    while (j + 1 < slice.runs.length && slice.runs[j + 1][0] - spanEnd <= 262_144) {
      j++;
      spanEnd = slice.runs[j][0] + slice.runs[j][1].reduce((a, b) => a + b, 0);
    }
    const from = slice.runs[i][0];
    const buf = await read(from, spanEnd);
    if (buf.length < spanEnd - from) throw new Error("The audio host returned less data than asked for");
    for (let r = i; r <= j; r++) {
      let p = slice.runs[r][0] - from;
      for (const s of slice.runs[r][1]) {
        adtsHeader(out, w, s, aac);
        out.set(buf.subarray(p, p + s), w + 7);
        w += s + 7;
        p += s;
      }
    }
    i = j + 1;
  }
  return out;
}

/** True if the bytes start like a raw ADTS AAC stream. */
export function isAdts(b: Uint8Array): boolean {
  return b.length >= 2 && b[0] === 0xff && (b[1] & 0xf6) === 0xf0;
}
