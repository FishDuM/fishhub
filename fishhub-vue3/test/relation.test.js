import assert from 'node:assert/strict'
import test from 'node:test'
import { shouldShowEmptyRelationState } from '../src/utils/relation.js'

test('keeps loading more available when an empty cursor page has a next cursor', () => {
  assert.equal(shouldShowEmptyRelationState({
    loading: false,
    userCount: 0,
    hasMore: true
  }), false)
})

test('shows the empty state only after the cursor is exhausted', () => {
  assert.equal(shouldShowEmptyRelationState({
    loading: false,
    userCount: 0,
    hasMore: false
  }), true)
})
