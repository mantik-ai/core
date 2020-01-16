package main

import (
	"bridge_debugger/debugger"
	"flag"
	"os"
)

var usage = `
Utility for Debugging Bridges

Commands
  
  read   -o <file> <url>                                     - Read a File format (Source) Bridge
  train  -i <file> -so <stats_file> -to <train result> <url> - Write to a Training Bridge
  apply  -i <file> -o <file> <url>                           - Apply some algorithm

  m2j    -i <file> -o <file>                                 - Convert Mantik Bundles to JSON Bundles.
  j2m    -i <file> -o <file>                                 - Convert JSON Bundle to Mantik bundle.

  select -i <file> -o <file> -c columns                      - Select some columns from a Mantik Bundle
  print -i <file> -n LINECOUNT								 - Print the first rows of a Bundle as JSON.

  run -mf <MantikHeader> [-keepDataDir] -data <zip-File> <image> - Run a Mantik Bridge via Docker with prepared data directory.

  Default url is http://localhost:8502.

  The URL must be a base of a listening bridge

  Enter command -help for a more specific help.
`

var DefaultUrl = "http://localhost:8502"

func main() {
	if len(os.Args) <= 1 {
		println("Command missing, usage")
		println(usage)
		os.Exit(1)
	}

	converterOptions := flag.NewFlagSet("", flag.ExitOnError)
	inputFile := converterOptions.String("i", "", "Input File")
	outputFile := converterOptions.String("o", "", "Output File")

	switch os.Args[1] {
	case "read":
		readOptions := flag.NewFlagSet("read", flag.ExitOnError)
		outputFile := readOptions.String("o", "", "Output file")
		readOptions.Parse(os.Args[2:])
		assertNonEmpty(*outputFile, "Output File")
		url := readOptions.Arg(0)
		if len(url) == 0 {
			url = DefaultUrl
		}
		debugger.ReadSourceToFile(url, *outputFile)
	case "train":
		trainOptions := flag.NewFlagSet("train", flag.ExitOnError)
		statsOut := trainOptions.String("so", "", "Stat Output")
		trainResult := trainOptions.String("to", "", "Train Result Output")
		inputFile := trainOptions.String("i", "", "Train Data Input")
		trainOptions.Parse(os.Args[2:])
		url := trainOptions.Arg(0)
		if len(url) == 0 {
			url = DefaultUrl
		}
		assertNonEmpty(*inputFile, "Input File")
		assertNonEmpty(*statsOut, "Stat Output")
		assertNonEmpty(*trainResult, "Train Result")
		debugger.TrainBridge(url, *inputFile, *statsOut, *trainResult)
	case "apply":
		applyOptions := flag.NewFlagSet("apply", flag.ExitOnError)
		inputFile := applyOptions.String("i", "", "Input File")
		outputFile := applyOptions.String("o", "", "Output File")
		applyOptions.Parse(os.Args[2:])
		url := applyOptions.Arg(0)
		if len(url) == 0 {
			url = DefaultUrl
		}
		assertNonEmpty(*inputFile, "input file")
		assertNonEmpty(*outputFile, "output file")
		debugger.ApplyBridge(url, *inputFile, *outputFile)
	case "m2j":
		converterOptions.Parse(os.Args[2:])
		assertNonEmpty(*inputFile, "input file")
		assertNonEmpty(*outputFile, "output file")
		debugger.MantikBundleToJson(*inputFile, *outputFile)
	case "j2m":
		converterOptions.Parse(os.Args[2:])
		assertNonEmpty(*inputFile, "input file")
		assertNonEmpty(*outputFile, "output file")
		debugger.JsonBundleToMantik(*inputFile, *outputFile)
	case "select":
		columns := converterOptions.String("c", "", "Comma-Separated Columns to select. There may be an optional : sign for renaming (Right side is target name)")
		converterOptions.Parse(os.Args[2:])
		parsedColumns, err := debugger.ParseSelectColumns(*columns)
		if err != nil {
			println("Invalid columns", err.Error())
			os.Exit(1)
		}
		assertNonEmpty(*inputFile, "input file")
		assertNonEmpty(*outputFile, "output file")
		debugger.SelectColumns(*inputFile, *outputFile, parsedColumns)
	case "print":
		printOptions := flag.NewFlagSet("print", flag.ExitOnError)
		inputFile := printOptions.String("i", "", "Input File")
		rows := printOptions.Int("n", 10, "Rows")
		printOptions.Parse(os.Args[2:])
		assertNonEmpty(*inputFile, "Input File")
		debugger.PrintBundle(*inputFile, *rows)
	case "run":
		runOptions := flag.NewFlagSet("run", flag.ExitOnError)
		mantikHeader := runOptions.String("mf", "", "MantikHeader")
		dataFile := runOptions.String("data", "", "Data ZIP file")
		keepDataDir := runOptions.Bool("keepDataDir", false, "Keep Data Directory")
		runOptions.Parse(os.Args[2:])
		image := runOptions.Arg(0)
		assertNonEmpty(*mantikHeader, "mf")
		assertNonEmpty(image, "image")
		debugger.RunBridge(image, *mantikHeader, *dataFile, *keepDataDir)
	default:
		println("Unknown command", os.Args[1])
		os.Exit(1)

	}
}

func assertNonEmpty(s string, name string) {
	if len(s) == 0 {
		println(name, "may not be empty")
		os.Exit(1)
	}
}
