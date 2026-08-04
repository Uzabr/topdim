// @vitest-environment jsdom
import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import GoogleLoginButton from './GoogleLoginButton';

describe('GoogleLoginButton', () => {
  afterEach(() => {
    cleanup();
    delete (window as { google?: unknown }).google;
  });

  it('initializes GIS and renders the button once the script (mocked as already loaded) is available', async () => {
    const initialize = vi.fn();
    const renderButton = vi.fn();
    window.google = { accounts: { id: { initialize, renderButton } } };

    render(<GoogleLoginButton clientId="test-client-id" onAuth={vi.fn()} />);

    await waitFor(() => expect(initialize).toHaveBeenCalledOnce());
    expect(initialize).toHaveBeenCalledWith(
      expect.objectContaining({ client_id: 'test-client-id' }),
    );
    expect(renderButton).toHaveBeenCalledOnce();
  });

  it('calls onAuth with the ID-token when GIS invokes the callback', async () => {
    let capturedCallback: ((response: { credential: string }) => void) | undefined;
    const initialize = vi.fn((config: { callback: (r: { credential: string }) => void }) => {
      capturedCallback = config.callback;
    });
    const renderButton = vi.fn();
    window.google = { accounts: { id: { initialize, renderButton } } };

    const onAuth = vi.fn();
    render(<GoogleLoginButton clientId="test-client-id" onAuth={onAuth} />);

    await waitFor(() => expect(capturedCallback).toBeDefined());
    capturedCallback?.({ credential: 'fake-id-token' });

    expect(onAuth).toHaveBeenCalledWith('fake-id-token');
  });

  it('degrades safely without rendering or touching GIS when client-id is empty', () => {
    const initialize = vi.fn();
    const renderButton = vi.fn();
    window.google = { accounts: { id: { initialize, renderButton } } };

    const { container } = render(<GoogleLoginButton clientId="" onAuth={vi.fn()} />);

    expect(container.firstChild).toBeNull();
    expect(initialize).not.toHaveBeenCalled();
    expect(renderButton).not.toHaveBeenCalled();
  });

  it('does not throw when window.google is unavailable and client-id is empty', () => {
    expect(() =>
      render(<GoogleLoginButton clientId="" onAuth={vi.fn()} />),
    ).not.toThrow();
  });
});
