package debugger

import "bridge_debugger/debugger/req"

func ReadSourceToFile(url string, file string) {
	println("Reading Source url ", url, "to file", file)
	reader, err := req.GetMantikBundle(url, "get")
	ExitOnError(err, "Getting Resource")

	err = writeStreamToFile(reader, file)
	ExitOnError(err, "Writing File")
}
