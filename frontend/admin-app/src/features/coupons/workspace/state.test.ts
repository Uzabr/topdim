import { describe, expect, it } from 'vitest';
import {
  nextWorkspaceSearch,
  parseWorkspaceState,
  workspaceStatuses,
} from './state';

describe('coupon workspace URL state', () => {
  it('uses safe defaults for an empty URL', () => {
    expect(parseWorkspaceState(new URLSearchParams())).toEqual({
      tab: 'new',
      view: 'table',
      search: '',
      merchantId: null,
      assignedModeratorId: null,
      page: 0,
      pageSize: 20,
    });
  });

  it('normalizes invalid enum, page, size, and ID values', () => {
    const state = parseWorkspaceState(new URLSearchParams(
      'tab=bad&view=bad&page=-2&size=21&merchantId=0&assignedModeratorId=-1',
    ));

    expect(state).toEqual({
      tab: 'new',
      view: 'table',
      search: '',
      merchantId: null,
      assignedModeratorId: null,
      page: 0,
      pageSize: 20,
    });
  });

  it.each([
    ['20', 20],
    ['50', 50],
    ['100', 100],
  ] as const)('accepts supported size=%s', (size, expected) => {
    const state = parseWorkspaceState(new URLSearchParams(`size=${size}`));

    expect(state.pageSize).toBe(expected);
  });

  it.each(['0', '21', '101', 'invalid'])('normalizes unsupported size=%s', (size) => {
    const state = parseWorkspaceState(new URLSearchParams(`size=${size}`));

    expect(state.pageSize).toBe(20);
  });

  it('parses and trims valid filters', () => {
    const state = parseWorkspaceState(new URLSearchParams(
      'tab=in-progress&view=kanban&search=%20pizza%20&merchantId=12&assignedModeratorId=7&page=3&size=50',
    ));

    expect(state).toEqual({
      tab: 'in-progress',
      view: 'kanban',
      search: 'pizza',
      merchantId: 12,
      assignedModeratorId: 7,
      page: 3,
      pageSize: 50,
    });
  });

  it('maps every product tab to its exact backend statuses', () => {
    expect(workspaceStatuses('new')).toEqual(['LEAD']);
    expect(workspaceStatuses('in-progress')).toEqual(['DRAFT']);
    expect(workspaceStatuses('revision')).toEqual(['REVISION_REQUESTED']);
    expect(workspaceStatuses('waiting-partner')).toEqual(['WAITING_FOR_MERCHANT']);
    expect(workspaceStatuses('published')).toEqual(['ACTIVE', 'PAUSED', 'SOLD_OUT']);
    expect(workspaceStatuses('archived')).toEqual(['ARCHIVED']);
  });

  it('preserves applicable filters and resets page when the tab changes', () => {
    const current = new URLSearchParams(
      'tab=new&view=kanban&search=pizza&merchantId=12&assignedModeratorId=7&page=4&size=50',
    );

    const next = nextWorkspaceSearch(current, { tab: 'revision' });

    expect(next.toString()).toBe(
      'tab=revision&view=kanban&search=pizza&merchantId=12&assignedModeratorId=7&page=0&size=50',
    );
  });

  it('resets page when a result filter changes', () => {
    const current = new URLSearchParams('tab=new&view=table&search=pizza&page=8&size=100');

    const next = nextWorkspaceSearch(current, { search: 'spa' });

    expect(next.get('search')).toBe('spa');
    expect(next.get('page')).toBe('0');
    expect(next.get('size')).toBe('100');
  });

  it('removes nullable filters without dropping unrelated state', () => {
    const current = new URLSearchParams(
      'tab=new&view=table&search=pizza&merchantId=12&assignedModeratorId=7&page=2&size=20',
    );

    const next = nextWorkspaceSearch(current, {
      merchantId: null,
      assignedModeratorId: null,
    });

    expect(next.has('merchantId')).toBe(false);
    expect(next.has('assignedModeratorId')).toBe(false);
    expect(next.get('search')).toBe('pizza');
    expect(next.get('page')).toBe('0');
  });
});
