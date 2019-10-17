package cli

import (
	"encoding/json"
	"flag"
	"fmt"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"io/ioutil"
	"os"
	"path"
)

// Implements the command line interface for serving

// Return Codes
const RC_INVALID_ARGUMENT = 1
const RC_COULD_NOT_LOAD_MANTIKFILE = 2
const RC_COULD_NOT_PARSE_MANTIKFILE = 3
const RC_COULD_NOT_LOAD_ALGORITHM = 4
const RC_COULD_NOT_ADAPT_ALGORITHM = 5
const RC_COULD_NOT_CREATE_SERVER = 6
const RC_COULD_NOT_START_SERFVER = 7

func printErrorAndQuit(code int, format string, a ...interface{}) {
	fmt.Fprintf(os.Stderr, format, a...)
	fmt.Fprint(os.Stderr, "\n")
	os.Exit(code)
}

func printHelp(args []string) {
	fmt.Printf(
		`Usage %s <command> <args>
Commands:
  help        - Show this help
  serve       - Serve with the help of a Mantikfile
  analyze     - Print Analysis

Mantikfile lookup is usually done by adding a directory as extra argument
`, args[0])
	fmt.Println("Usage", args[0], "<command>", "<args>")
}

func Start(args []string, backend serving.Backend) {
	serveCommand := flag.NewFlagSet("serve", flag.ExitOnError)
	port := serveCommand.Int("port", 8502, "Port")

	analyzeCommand := flag.NewFlagSet("analyze", flag.ExitOnError)

	if len(args) < 2 {
		printHelp(args)
		os.Exit(RC_INVALID_ARGUMENT)
	}

	switch args[1] {
	case "help":
		fallthrough
	case "--help":
		fallthrough
	case "-help":
		printHelp(args)
		return
	case "serve":
		serveCommand.Parse(args[2:])
	case "analyze":
		analyzeCommand.Parse(args[2:])
	default:
		fmt.Fprintf(os.Stderr, "Unknown commands %s\n", args[1])
		os.Exit(RC_INVALID_ARGUMENT)
	}

	if serveCommand.Parsed() {
		dir, mantikFile := loadMantikfile(serveCommand)
		executable := loadAndAdapt(backend, dir, mantikFile)
		address := fmt.Sprintf(":%d", *port)
		server, err := server.CreateServerForExecutable(executable, address)
		if err != nil {
			printErrorAndQuit(RC_COULD_NOT_CREATE_SERVER, "Could not create server %s", err.Error())
		}
		fmt.Printf("Start listening on %d\n", *port)
		err = server.ListenAndServe()
		if err != nil {
			printErrorAndQuit(RC_COULD_NOT_START_SERFVER, "Could not start server %s", err.Error())
		}
		return
	}
	if analyzeCommand.Parsed() {
		dir, mantikfile := loadMantikfile(analyzeCommand)
		executable := loadAndAdapt(backend, dir, mantikfile)
		serialized, err := json.Marshal(executable.ExtensionInfo())
		if err != nil {
			printErrorAndQuit(100, "Could not serialize info")
		}
		fmt.Fprint(os.Stdout, string(serialized))
		return
	}
}

func loadAndAdapt(backend serving.Backend, dirName string, mantikFile serving.Mantikfile) serving.Executable {
	payloadDir := path.Join(dirName, "payload")
	var payloadArg *string
	if fileExists(payloadDir) {
		payloadArg = &payloadDir
	} else {
		payloadArg = nil
	}
	executable, err := backend.LoadModel(payloadArg, mantikFile)

	if err != nil {
		printErrorAndQuit(RC_COULD_NOT_LOAD_ALGORITHM, "Could not load executable %s", err.Error())
		return nil
	}

	// trying to bridge it to the expected format
	adapted, err := serving.AutoAdapt(executable, mantikFile)
	if err != nil {
		executable.Cleanup()
		printErrorAndQuit(RC_COULD_NOT_ADAPT_ALGORITHM, "Could not adapt executable %s", err.Error())
		return nil
	}
	return adapted
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return !os.IsNotExist(err)
}

// Returns directory of mantik file and parsed mantik file
func loadMantikfile(flagSet *flag.FlagSet) (string, serving.Mantikfile) {
	extraDir := "."
	if flagSet.NArg() > 0 {
		extraDir = flagSet.Arg(0)
	}
	completeDir := extraDir + "/"
	mantikFile, err := ioutil.ReadFile(completeDir + "Mantikfile")

	if err != nil {
		fmt.Fprintf(os.Stderr, "Could not read Mantikfile: %s\n", err.Error())
		os.Exit(RC_COULD_NOT_LOAD_MANTIKFILE)
	}
	parsedMantikFile, err := serving.ParseMantikFile(mantikFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Could not parse Mantikfile %s\n", err.Error())
		os.Exit(RC_COULD_NOT_PARSE_MANTIKFILE)
	}
	return completeDir, parsedMantikFile
}
