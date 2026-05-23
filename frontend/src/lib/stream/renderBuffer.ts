export class RenderBuffer {
  private buffer = "";
  private rafId: number | null = null;
  private onFlush: (text: string) => void;
  private disposed = false;

  constructor(onFlush: (text: string) => void) {
    this.onFlush = onFlush;
  }

  push(delta: string): void {
    if (this.disposed) return;
    this.buffer += delta;
    if (this.rafId === null) {
      this.rafId = requestAnimationFrame(() => this.flush());
    }
  }

  flushImmediate(): void {
    if (this.disposed) return;
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    this.doFlush();
  }

  getLength(): number {
    return this.buffer.length;
  }

  dispose(): void {
    this.disposed = true;
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    this.buffer = "";
  }

  private flush(): void {
    this.rafId = null;
    this.doFlush();
  }

  private doFlush(): void {
    if (this.buffer.length === 0) return;
    const text = this.buffer;
    this.buffer = "";
    this.onFlush(text);
  }
}
