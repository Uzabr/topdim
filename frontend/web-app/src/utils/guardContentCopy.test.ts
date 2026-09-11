// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { guardContentCopy } from './guardContentCopy';

describe('guardContentCopy', () => {
  afterEach(() => {
    document.body.replaceChildren();
  });

  it('prevents copying page text and allows copying from an input', () => {
    guardContentCopy();

    const paragraph = document.createElement('p');
    paragraph.textContent = 'secret offer';
    const input = document.createElement('input');
    input.value = '998901234567';
    document.body.append(paragraph, input);

    const pageCopy = new Event('copy', { cancelable: true, bubbles: true });
    paragraph.dispatchEvent(pageCopy);
    expect(pageCopy.defaultPrevented).toBe(true);

    const fieldCopy = new Event('copy', { cancelable: true, bubbles: true });
    input.dispatchEvent(fieldCopy);
    expect(fieldCopy.defaultPrevented).toBe(false);
  });
});
