package streamer

import "time"

/* Notification Callback. */
type StreamNotifyFn = func(reqId int, in int64, out int64, inFailure error, done bool)

/* Notification Delay for Streams. */
const StreamNotificationDelay = time.Second

/* Header name for Content Type. */
const HttpContentType = "Content-Type"
const HttpAccept = "Accept"
