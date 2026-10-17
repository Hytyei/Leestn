export interface Env {
  DB: D1Database;
  AI: Ai;
  TRANSCRIBE: Queue<ChunkJob>;
  /** The invite key (`wrangler secret put INVITE_KEY`). Needed to create an account and, before that, to use the server. */
  INVITE_KEY?: string;
  MAX_TRANSCRIPTS_PER_DAY: string;
  MAX_PER_USER_PER_DAY: string;
  CHUNK_BYTES: string;
  MAX_AUDIO_MB: string;
  ALLOW_ORIGIN: string;
}

export interface ChunkJob {
  epId: string;
  idx: number;
}

export interface Segment {
  start: number;
  end: number;
  text: string;
}
