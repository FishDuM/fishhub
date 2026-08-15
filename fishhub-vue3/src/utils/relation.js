export const shouldShowEmptyRelationState = ({ loading, userCount, hasMore }) =>
  !loading && userCount === 0 && !hasMore
