let loaderAudio: HTMLAudioElement | null = null;

/**
 * Plays the short ORCHORDS loader chime when assistant generation starts.
 * Degrades silently when the browser blocks autoplay or audio is unsupported.
 */
export function playLoaderSound(): void {
  try {
    loaderAudio ??= new Audio("/loader-sound.mp3");
    loaderAudio.currentTime = 0;
    loaderAudio.play()?.catch(() => {});
  } catch {
    // Autoplay blocked or Audio unavailable: no-op.
  }
}
