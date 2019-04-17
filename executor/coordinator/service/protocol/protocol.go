package protocol

import "errors"

// Calls from Coordinator --> SideCar

type ResponseBase struct {
	Error string
}

func (r *ResponseBase) IsError() bool {
	return len(r.Error) > 0
}

func (r *ResponseBase) Err() error {
	if r.IsError() {
		return errors.New(r.Error)
	} else {
		return nil
	}
}

/* A Request for providing a new HTTP GET Stream. */
type RequestStream struct {
	// identification, used for Status Information
	Id string
	// The resource which should be served
	Resource string
	// The (optional) content type which should be used
	ContentType *string
}

/* Response for requesting a stream. */
type RequestStreamResponse struct {
	Port int
	ResponseBase
}

/** A Request for transmitting data to a Http Resource. */
type RequestTransfer struct {
	// identification, used for Status Information
	Id string
	// Destination, can be a resource or an URL
	Destination string
	// The (optional) content type which should be used
	ContentType *string
	// Source URL (tcp:// or http://)
	Source string
}

/** Response for RequestTransfer Request. */
type RequestTransferResponse struct {
	ResponseBase
}

/** A Request for a transformation through a HTTP Post Resource. */
type RequestTransformation struct {
	// identification, used for Status Information
	Id string
	// Resource which will do the Transformation
	Destination string
	// The (optional) content type which should be used
	ContentType *string
	// Source URL (tcp:// or http://)
	Source string
}

/** The response for a transformation through a HTTP Post resource. */
type RequestTransformationResponse struct {
	Port int
	ResponseBase
}

// Init Calls Coordinator --> SideCar
type ClaimSideCarRequest struct {
	CoordinatorAddress string
	Name               string // Name of the side car
	Challenge          []byte // Which has to be pinged back
}

// Response for ClaimSideCarRequest
type ClaimSideCarResponse struct {
	ResponseBase
}

type QuitSideCarRequest struct {
}

type QuitSideCarResponse struct {
	ResponseBase
}

// Calls from SideCar --> Coordinator

type HelloCoordinatorRequest struct {
	Name      string // name of the side car
	Challenge []byte // back informed challenge
}

type HelloCoordinatorResponse struct {
	ResponseBase
}

type StatusUpdate struct {
	Name          string // name of the side car
	Challenge     []byte // back informed challenge
	Id            string // name of the stream
	Ingress       int64
	Outgress      int64
	Error         string
	HttpErrorCode int
	Done          bool
}

type StatusUpdateResponse struct {
	ResponseBase
}

// Request for each RPC Server to shut down now
// Service Name "Generic.Cancel"
type CancelRequest struct {
	Reason string
}

type CancelResponse struct {
	ResponseBase
}
