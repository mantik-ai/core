package cmd

import (
	"errors"
	"flag"
	"fmt"
)

const usage = `
	Mantik Command Line Client

	Commands:
    
	version       - display engine version
	items         - list items
	item          - show mantik item

	Enter: <call> -help to get a list of command line arguments.
`

const DefaultPort = 8087
const DefaultHost = "127.0.0.1"

// Common Arguments
type CommonArguments struct {
	// Engine Address
	Host string
	Port int
}

// Show version Arguments
type VersionArguments struct {
}

// Show items arguments
type ItemsArguments struct {
	Deployed  bool
	Anonymous bool
	Kind      string
	NoTable   bool
}

// Show single item arguments
type ItemArguments struct {
	MantikId string
}

type Arguments struct {
	Common CommonArguments

	// if set the argument is requested
	Version *VersionArguments
	Items   *ItemsArguments
	Item    *ItemArguments
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
var MissingArgument = errors.New("missing argument")

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
	case "items":
		args.Items = &ItemsArguments{}
		items := flag.NewFlagSet("items", flag.ContinueOnError)
		addCommonArgs(items, &args.Common)
		items.BoolVar(&args.Items.Anonymous, "a", false, "Also list anonymous items")
		items.BoolVar(&args.Items.Deployed, "d", false, "List deployed items")
		items.StringVar(&args.Items.Kind, "k", "", "Filter for a specific kind")
		items.BoolVar(&args.Items.NoTable, "notable", false, "Render pure text instead of table")
		err = items.Parse(rest)
	case "item":
		args.Item = &ItemArguments{}
		item := flag.NewFlagSet("item", flag.ContinueOnError)
		item.Usage = func() {
			fmt.Fprintf(item.Output(), "Usage of %s [options] <mantikId>\n", item.Name())
			item.PrintDefaults()
		}
		addCommonArgs(item, &args.Common)
		err = item.Parse(rest)
		if err == nil {
			if item.NArg() < 1 {
				println("Expected one argument <mantikId>")
				err = MissingArgument
			} else {
				args.Item.MantikId = item.Arg(0)
			}
		}
	default:
		println("Unexpected command", cmd)
		printUsage()
		err = UnexpectedCommand
	}
	return &args, err
}
