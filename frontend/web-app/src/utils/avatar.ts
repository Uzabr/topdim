export const MAX_AVATAR_FILE_SIZE = 20 * 1024 * 1024;

export type AvatarFileError = 'type' | 'size' | null;

export function validateAvatarFile(file: File): AvatarFileError {
  if (!file.type.startsWith('image/')) return 'type';
  if (file.size > MAX_AVATAR_FILE_SIZE) return 'size';
  return null;
}
