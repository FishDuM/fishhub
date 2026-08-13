export const useLatestRequest = () => {
  let currentRequestId = 0

  const begin = () => ++currentRequestId

  const isCurrent = (requestId) => requestId === currentRequestId

  return { begin, isCurrent }
}
