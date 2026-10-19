export interface Env {
  DB: D1Database;
  AI: Ai;
  TRANSCRIBE: Queue<ChunkJob>;
  /** The invite key (`wrangler secret put INVITE_KEY`). Needed to create an account and, before that, to use the server. */
  INVITE_KEY?: string;
  MAX_TRANSCRIPTS_PER_DAY: string;
  MAX_PER_USER_PER_DAY: string;
  CHUNK_BYTES: string;
  WHISPER_DAILY_MINUTES?: string;
  MAX_AUDIO_MB: string;
  ALLOW_ORIGIN: string;
}

export interface ChunkJob {
  epId: string;
  idx: number;
  /** Present for M4A/MP4 audio: which frames make up this piece. Messages without it are MP3 byte ranges. */
  aac?: {
    t0: number;
    runs: [number, number[]][];
    profile: number;
    sfi: number;
    channels: number;
  };
  /** Raw AAC (ADTS) files are cut at byte positions like MP3. */
  fmt?: "adts";
  /** Spoken language (ISO code such as "de"), from the podcast feed. Stops the model guessing the language slice by slice. */
  lang?: string;
  /** MP3: size of the ID3 tag at the start of the file (cover art can be large) and the total file size. */
  id3?: number;
  sz?: number;
}

export interface Segment {
  start: number;
  end: number;
  text: string;
}
