let count = 0
const map = {}

interface Window {
  bridge: {
    showToast: (s: string) => void
    fetch: (url: string, method: string, headers: string, body: string, stamp: string) => void
  }
  freeFetch: typeof fetch
  requestResolve: (stamp: string, status: number, statusText: string, headers: Record<string, string>) => void
  requestFail: (stamp: string, error?: string) => void
  streamPush: (stamp: string, line: number[]) => void
  streamSucceed: (stamp: string) => void
  streamError: (stamp: string, error?: string) => void
}
window.freeFetch = async (input, init) => {
  const id = (count++).toString()
  map[id] = {}
  const p = new Promise<Response>((res, rej) => {
    map[id].res = res
    map[id].rej = rej
  })

  console.log(init)
  const request = new Request(input, {
    method: init?.method,
    headers: init?.headers,
    body: init?.body
  })
  console.log(request, [...request.headers])
  let headers = {
    ...Object.fromEntries([...request.headers, ...(
      Array.isArray(init?.headers) || init?.headers instanceof Headers
        ? init?.headers
        : Object.entries(init?.headers ?? {}))]),
  }
  let body = "[]"

  const buffer = new Uint8Array(await request.arrayBuffer())
  body = JSON.stringify(Array.from(buffer))

  window.bridge.fetch(request.url, request.method, JSON.stringify(headers), body, id)
  return p
}
window.requestResolve = (id, status, statusText, headersObj) => {
  console.log('requestResolve', id, status, statusText, headersObj)
  const headers = new Headers(headersObj)
  let stream = new ReadableStream({
    start(controller) {
      map[id].controller = controller
    },
  })
  if (headers.get('content-encoding') === 'gzip') {
    stream = stream.pipeThrough(new DecompressionStream('gzip'))
  }
  const ret = new Response(stream, {
    status,
    headers,
    statusText,
  })
  map[id].res(ret)
}
window.requestFail = (id, e) => {
  map[id].rej(new Error(e))
  map[id] = undefined
}
window.streamPush = (id, line) => {
  console.log('requestPush')
  console.log(id, line)
  const controller = map[id].controller
  const arr = new Uint8Array((line))
  controller.enqueue(arr)
}
window.streamSucceed = (id) => {
  console.log('streamSucceed', id)
  const controller = map[id].controller
  controller.close()
  map[id] = undefined
}

window.streamError = (id, e) => {
  console.log('streamError', id)
  const controller = map[id].controller
  controller.error(new Error(e || "StreamError"))
  map[id] = undefined
}
