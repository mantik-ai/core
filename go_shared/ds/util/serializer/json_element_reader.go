package serializer

import (
	"bufio"
	"bytes"
	"encoding/json"
	"github.com/pkg/errors"
	"io"
)

type ElementType int

var maxDepth = 100
var stackOverflow = errors.New("Stack Overflow in JSON Tokenizing")

func isWhitespace(b byte) bool {
	return b == ' ' || b == '\t' || b == '\r' || b == '\n'
}

// Helper which decodes JSON into a stream of Elements which can be read like MsgPack

const (
	String       = ElementType(iota) // contains parsed value
	OtherLiteral                     // Number, Boolean, true/false, contains it unparsed as string
	Array                            // value contains array length
	Object                           // value contains array length
)

type jsonElement struct {
	elementType ElementType
	value       interface{}
}

func readJsonElements(reader *bufio.Reader) ([]jsonElement, error) {
	var result []jsonElement
	err := readJsonElementsToResult(0, reader, &result)
	return result, err
}

func readJsonElementsToResult(depth int, reader *bufio.Reader, result *[]jsonElement) error {
	if depth > maxDepth {
		return stackOverflow
	}
	start, err := peekNextNonWhiteSpace(reader)
	if err != nil {
		return err
	}
	switch start {
	case '"':
		err = readJsonStringLiteral(reader, result)
	case '[':
		err = readJsonArray(depth+1, reader, result)
	case '{':
		err = readJsonObject(depth+1, reader, result)
	default:
		err = readJsonOtherLiteral(reader, result)
	}
	// If peek non white above reads it, the string should go out.
	if err == io.EOF {
		return io.ErrUnexpectedEOF
	}
	return err
}

func readJsonArray(depth int, reader *bufio.Reader, result *[]jsonElement) error {
	char, err := readUntilNextNonWhitespace(reader)
	if err != nil {
		return err
	}
	if char != '[' {
		return errors.Errorf("Expected [, got %d", char)
	}
	var count = 0
	var subElements []jsonElement
	for {
		byte, err := peekNextNonWhiteSpace(reader)
		if err != nil {
			return err
		}
		if byte == ']' {
			// Done
			_, err = reader.ReadByte()
			if err != nil {
				return err
			}
			*result = append(*result, jsonElement{Array, count})
			*result = append(*result, subElements...)
			return nil
		}
		err = readJsonElementsToResult(depth, reader, &subElements)
		if err != nil {
			return err
		}
		byte, err = peekNextNonWhiteSpace(reader)
		if err != nil {
			return err
		}
		if byte == ',' {
			if _, err = reader.ReadByte(); err != nil {
				return err
			}
		} else if byte == ']' {
			// ok, will be catched in next iteration
		} else {
			return errors.Errorf("Unexpected character %d", byte)
		}
		count += 1
	}
}

func readJsonObject(depth int, reader *bufio.Reader, result *[]jsonElement) error {
	char, err := reader.ReadByte()
	if err != nil {
		return err
	}
	if char != '{' {
		return errors.Errorf("Expected {, got %d", char)
	}
	var count = 0
	var subElements []jsonElement
	for {
		byte, err := peekNextNonWhiteSpace(reader)
		if err != nil {
			return err
		}
		if byte == '}' {
			// Done
			_, err = reader.ReadByte()
			if err != nil {
				return err
			}
			*result = append(*result, jsonElement{Object, count})
			*result = append(*result, subElements...)
			return nil
		}
		err = readJsonElementsToResult(depth, reader, &subElements)
		if err != nil {
			return err
		}
		byte, err = readUntilNextNonWhitespace(reader)
		if err != nil {
			return err
		}
		if byte != ':' {
			return errors.Errorf("Expected :, got %d", byte)
		}
		err = readJsonElementsToResult(depth, reader, &subElements)
		if err != nil {
			return err
		}
		byte, err = peekNextNonWhiteSpace(reader)
		if byte == ',' {
			_, err = reader.ReadByte()
			if err != nil {
				return err
			}
		} else if byte == '}' {
			// will be read in next iteration
		} else {
			return errors.Errorf("Unexpected character %d", byte)
		}
		count += 1
	}
}

func readJsonStringLiteral(reader *bufio.Reader, result *[]jsonElement) error {
	out := bytes.Buffer{}
	char, err := readUntilNextNonWhitespace(reader)
	if err != nil {
		return err
	}
	if char != '"' {
		return errors.Errorf("Expected \", got %d", char)
	}
	if err = out.WriteByte(char); err != nil {
		return err
	}
	escaped := false
	for {
		char, err := reader.ReadByte()
		if err != nil {
			return err
		}
		if err = out.WriteByte(char); err != nil {
			return err
		}
		if char == '"' && !escaped {
			var s string
			err := json.Unmarshal(out.Bytes(), &s)
			if err != nil {
				return err
			}
			*result = append(*result, jsonElement{String, s})
			return err
		}
		if escaped {
			escaped = false
		}
		if char == '\\' {
			escaped = true
		}
	}
}

func readJsonOtherLiteral(reader *bufio.Reader, result *[]jsonElement) error {
	out := bytes.Buffer{}
	for {
		char, err := reader.ReadByte()
		if err == io.EOF {
			*result = append(*result, jsonElement{OtherLiteral, string(out.Bytes())})
			// ok
			return nil
		}
		if err != nil {
			return err
		}
		if isWhitespace(char) || char == ',' || char == '{' || char == '}' || char == '"' || char == ':' || char == ']' {
			err := reader.UnreadByte()
			if err != nil {
				return err
			}
			*result = append(*result, jsonElement{OtherLiteral, string(out.Bytes())})
			// Done
			return nil
		}
		if err = out.WriteByte(char); err != nil {
			return err
		}
	}
}

/** Read the next non whitespace. */
func readUntilNextNonWhitespace(reader *bufio.Reader) (byte, error) {
	for {
		byte, err := reader.ReadByte()
		if err != nil {
			return 0, err
		}
		if !isWhitespace(byte) {
			return byte, nil
		}
	}
}

func peekNextNonWhiteSpace(reader *bufio.Reader) (byte, error) {
	for {
		byte, err := reader.ReadByte()
		if err != nil {
			return 0, err
		}
		if !isWhitespace(byte) {
			if err := reader.UnreadByte(); err != nil {
				return 0, err
			}
			return byte, nil
		}
	}
}
