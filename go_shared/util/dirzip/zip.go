package dirzip

import (
	"archive/zip"
	"bytes"
	"fmt"
	"github.com/pkg/errors"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
)

var separatorString = string(os.PathSeparator)

func ZipDirectory(directory string, debug bool) (string, error) {
	outFile, err := ioutil.TempFile("", "*.zip")
	defer outFile.Close()
	if err != nil {
		return "", err
	}

	if debug {
		println("Writing", outFile.Name())
	}
	err = ZipDirectoryToStream(directory, debug, outFile)
	return outFile.Name(), err
}

func ZipDirectoryToStream(directory string, debug bool, out io.Writer) error {
	stat, err := os.Stat(directory)
	if err != nil {
		return errors.Wrap(err, "could not stat directory")
	}
	if !stat.IsDir() {
		return errors.New("not a directory")
	}

	writer := zip.NewWriter(out)
	err = addDirectory(writer, directory, "", debug)
	if err != nil {
		return err
	}
	err = writer.Close()
	if err != nil {
		return errors.Wrap(err, "error on closing")
	}
	if debug {
		println("Writing file done")
	}
	return err
}

func addDirectory(writer *zip.Writer, directory, zipDirectory string, debug bool) error {
	if isHidden(directory) {
		if debug {
			println("Skipping directory", directory, "it's hidden")
		}
		return nil
	}
	if debug {
		println("Adding Directory", directory, "in", zipDirectory)
	}
	files, err := ioutil.ReadDir(directory)
	if err != nil {
		if debug {
			println("Error reading directory", err.Error())
		}
		return err
	}
	for _, file := range files {
		if file.IsDir() {
			newZipDirectory := zipDirectory + file.Name() + "/"
			err = addDirectory(writer, directory+separatorString+file.Name(), newZipDirectory, debug)
			if err != nil {
				return err
			}
		} else {
			addFile(writer, file.Name(), directory+separatorString+file.Name(), zipDirectory, debug)
			if err != nil {
				return err
			}
		}
	}
	return nil
}

func isHidden(filename string) bool {
	return strings.HasPrefix(filepath.Base(filename), ".")
}

func addFile(writer *zip.Writer, fileBaseName, filePath string, zipDirectory string, debug bool) error {
	if isHidden(filePath) {
		if debug {
			println("Skipping", filePath, "because it's hidden")
		}
		return nil
	}
	if debug {
		println("  Adding file", fileBaseName)
	}
	fileWriter, err := writer.Create(zipDirectory + fileBaseName)
	if err != nil {
		return errors.Wrap(err, fmt.Sprintf("Could not write %s", filePath))
	}
	content, err := ioutil.ReadFile(filePath)
	if err != nil {
		return errors.Wrap(err, fmt.Sprintf("Could not read file %s", filePath))
	}
	_, err = fileWriter.Write(content)
	if err != nil {
		return errors.Wrap(err, fmt.Sprintf("Could not write file %s", filePath))
	}
	return nil
}

func UnzipDiectory(zipFile string, directory string, debug bool) error {
	reader, err := zip.OpenReader(zipFile)
	if err != nil {
		return errors.Wrap(err, "Could not open ZIP File")
	}
	defer reader.Close()
	return unzipDiectoryFromReader(&reader.Reader, directory, debug)
}

func UnzipDirectoryFromZipBuffer(buffer bytes.Buffer, directory string, debug bool) error {
	reader := bytes.NewReader(buffer.Bytes())
	zipReader, err := zip.NewReader(reader, reader.Size())
	if err != nil {
		return err
	}
	return unzipDiectoryFromReader(zipReader, directory, debug)
}

func unzipDiectoryFromReader(reader *zip.Reader, directory string, debug bool) error {
	for _, file := range reader.File {
		filePath := filepath.Join(directory, file.Name)
		// Source: https://golangcode.com/unzip-files-in-go/
		// And https://snyk.io/research/zip-slip-vulnerability#go
		if !strings.HasPrefix(filePath, filepath.Clean(directory)+string(os.PathSeparator)) {
			return errors.Errorf("%s illegal file path", filePath)
		}
		if file.FileInfo().IsDir() {
			if debug {
				println("Creating Directory", filePath)
			}
			os.MkdirAll(filePath, os.ModePerm)
		} else {
			if debug {
				println("Unpacking", filePath)
			}
			err := os.MkdirAll(filepath.Dir(filePath), os.ModePerm)
			if err != nil {
				return errors.Errorf("Could not ensure directory of %s", filePath)
			}
			outFile, err := os.OpenFile(filePath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, file.Mode())
			if err != nil {
				return errors.Errorf("Could not create file %s", filePath)
			}

			rc, err := file.Open()
			if err != nil {
				return err
			}
			defer rc.Close()

			_, err = io.Copy(outFile, rc)
			if err != nil {
				return errors.Wrap(err, "Could not write file")
			}
		}
	}
	return nil
}
