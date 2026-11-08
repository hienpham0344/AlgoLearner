export function validateAvatar(file: Pick<File, 'type' | 'size'>): string | null {
  if (!['image/png', 'image/jpeg'].includes(file.type)) return 'Choose a PNG or JPEG image.';
  if (file.size > 5 * 1024 * 1024) return 'Image is larger than the 5 MB limit.';
  if (file.size === 0) return 'Choose a non-empty image.';
  return null;
}
