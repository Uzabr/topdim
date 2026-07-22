// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { MAX_AVATAR_FILE_SIZE, validateAvatarFile } from './avatar';

function fileWithSize(name: string, type: string, size: number): File {
  const file = new File(['content'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('avatar validation', () => {
  it('accepts an image exactly at the backend size limit', () => {
    expect(validateAvatarFile(fileWithSize('avatar.webp', 'image/webp', MAX_AVATAR_FILE_SIZE))).toBeNull();
  });

  it('rejects a non-image even when the extension looks like an image', () => {
    expect(validateAvatarFile(fileWithSize('avatar.png', 'text/plain', 100))).toBe('type');
  });

  it('rejects an image larger than 20 MiB', () => {
    expect(
      validateAvatarFile(fileWithSize('avatar.jpg', 'image/jpeg', MAX_AVATAR_FILE_SIZE + 1)),
    ).toBe('size');
  });
});
