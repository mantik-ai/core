package cmd

import (
	"errors"
	"flag"
)

const usage = `
	Mantik Command Line Client

	Commands:
    
	version       - display engine version

	Enter: <call> -help to get a list of command line arguments.
`

const DefaultPort = 8087
const DefaultHost = "127.0.0.1"

type CommonArguments struct {
	// Engine Address
	Host string
	Port int
}

type VersionArguments struct {
}

type Arguments struct {
	Common CommonArguments

	// if set the argument is requested
	Version *VersionArguments
}

func addCommonArgs(set *flag.FlagSet, arguments *CommonArguments) {
	set.IntVar(&arguments.Port, "port", DefaultPort, "Mantik Engine Port")
	set.StringVar(&arguments.Host, "host", DefaultHost, "Mantik Engine Host")
}

func printUsage() {
	println(usage)
}

var MissingCommand = errors.New("missing command")
var UnexpectedCommand = errors.New("unexpected command")

// Parse arguments. Error messages are already printed.
func ParseArguments(argv []string) (*Arguments, error) {
	if len(argv) <= 1 {
		println("Missing Command")
		printUsage()
		return nil, MissingCommand
	}
	cmd := argv[1]
	rest := argv[2:]

	var args = Arguments{}

	var err error
	switch cmd {
	case "version":
		args.Version = &VersionArguments{}
		about := flag.NewFlagSet("version", flag.ContinueOnError)
		addCommonArgs(about, &args.Common)
		err = about.Parse(rest)
	default:
		println("Unexpected command", cmd)
		printUsage()
		err = UnexpectedCommand
	}
	return &args, err
}
