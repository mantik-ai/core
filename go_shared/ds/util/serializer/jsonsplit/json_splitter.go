package jsonsplit

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

// A Single JSON Element, for a description see above
type JsonElement struct {
	ElementType ElementType
	Value       interface{}
}

type JsonSplitter struct {
	reader          *bufio.Reader
	isArraySplitter bool
}

// Create a new Json Splitter
func MakeJsonSplitter(data []byte) *JsonSplitter {
	return &JsonSplitter{bufio.NewReader(bytes.NewReader(data)), false}
}

/** Create a new splitter, which starts by returning the simple array elements. */
func MakeJsonSplitterForArray(data []byte) (*JsonSplitter, error) {
	splitter := MakeJsonSplitter(data)
	start, err := splitter.readNextNonWhitespace()
	if err != nil {
		return nil, err
	}
	if start != '[' {
		return nil, errors.Errorf("Expected array start, got %s", string(start))
	}
	splitter.isArraySplitter = true
	return splitter, nil
}

func (j *JsonSplitter) ReadJsonElements() ([]JsonElement, error) {
	var result []JsonElement
	err := j.readJsonElementsToResult(0, &result)
	return result, err
}

func (j *JsonSplitter) readJsonElementsToResult(depth int, result *[]JsonElement) error {
	if depth > maxDepth {
		return stackOverflow
	}
	start, err := j.peekNextNonWhiteSpace()
	if err != nil {
		return err
	}
	switch start {
	case '"':
		err = j.readJsonStringLiteral(result)
	case '[':
		err = j.readJsonArray(depth+1, result)
	case '{':
		err = j.readJsonObject(depth+1, result)
	case ',':
		if depth == 0 && j.isArraySplitter {
			_, err = j.readNextNonWhitespace()
			if err != nil {
				// should not happen
				return err
			}
			return j.readJsonElementsToResult(0, result)
		} else {
			return errors.New("Unexpected ','")
		}
	case ']':
		if depth == 0 && j.isArraySplitter {
			_, err := j.readNextNonWhitespace()
			if err != nil {
				// should not happen
				return err
			}
			// the array is over
			return io.EOF
		} else {
			return errors.New("Unexpected ']")
		}
	default:
		err = j.readJsonOtherLiteral(result)
	}
	// If peek non white above reads it, the string should go out.
	if err == io.EOF {
		return io.ErrUnexpectedEOF
	}
	return err
}

func (j *JsonSplitter) readJsonArray(depth int, result *[]JsonElement) error {
	char, err := j.readNextNonWhitespace()
	if err != nil {
		return err
	}
	if char != '[' {
		return errors.Errorf("Expected [, got %d", char)
	}
	var count = 0
	var subElements []JsonElement
	for {
		byte, err := j.peekNextNonWhiteSpace()
		if err != nil {
			return err
		}
		if byte == ']' {
			// Done
			_, err = j.reader.ReadByte()
			if err != nil {
				return err
			}
			*result = append(*result, JsonElement{Array, count})
			*result = append(*result, subElements...)
			return nil
		}
		err = j.readJsonElementsToResult(depth, &subElements)
		if err != nil {
			return err
		}
		byte, err = j.peekNextNonWhiteSpace()
		if err != nil {
			return err
		}
		if byte == ',' {
			if _, err = j.reader.ReadByte(); err != nil {
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

func (j *JsonSplitter) readJsonObject(depth int, result *[]JsonElement) error {
	char, err := j.reader.ReadByte()
	if err != nil {
		return err
	}
	if char != '{' {
		return errors.Errorf("Expected {, got %d", char)
	}
	var count = 0
	var subElements []JsonElement
	for {
		byte, err := j.peekNextNonWhiteSpace()
		if err != nil {
			return err
		}
		if byte == '}' {
			// Done
			_, err = j.reader.ReadByte()
			if err != nil {
				return err
			}
			*result = append(*result, JsonElement{Object, count})
			*result = append(*result, subElements...)
			return nil
		}
		err = j.readJsonElementsToResult(depth, &subElements)
		if err != nil {
			return err
		}
		byte, err = j.readNextNonWhitespace()
		if err != nil {
			return err
		}
		if byte != ':' {
			return errors.Errorf("Expected :, got %d", byte)
		}
		err = j.readJsonElementsToResult(depth, &subElements)
		if err != nil {
			return err
		}
		byte, err = j.peekNextNonWhiteSpace()
		if byte == ',' {
			_, err = j.reader.ReadByte()
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

func (j *JsonSplitter) readJsonStringLiteral(result *[]JsonElement) error {
	out := bytes.Buffer{}
	char, err := j.readNextNonWhitespace()
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
		char, err := j.reader.ReadByte()
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
			*result = append(*result, JsonElement{String, s})
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

func (j *JsonSplitter) readJsonOtherLiteral(result *[]JsonElement) error {
	out := bytes.Buffer{}
	for {
		char, err := j.reader.ReadByte()
		if err == io.EOF {
			*result = append(*result, JsonElement{OtherLiteral, string(out.Bytes())})
			// ok
			return nil
		}
		if err != nil {
			return err
		}
		if isWhitespace(char) || char == ',' || char == '{' || char == '}' || char == '"' || char == ':' || char == ']' {
			err := j.reader.UnreadByte()
			if err != nil {
				return err
			}
			*result = append(*result, JsonElement{OtherLiteral, string(out.Bytes())})
			// Done
			return nil
		}
		if err = out.WriteByte(char); err != nil {
			return err
		}
	}
}

/** Read the next non whitespace. */
func (j *JsonSplitter) readNextNonWhitespace() (byte, error) {
	for {
		byte, err := j.reader.ReadByte()
		if err != nil {
			return 0, err
		}
		if !isWhitespace(byte) {
			return byte, nil
		}
	}
}

func (j *JsonSplitter) peekNextNonWhiteSpace() (byte, error) {
	for {
		byte, err := j.reader.ReadByte()
		if err != nil {
			return 0, err
		}
		if !isWhitespace(byte) {
			if err := j.reader.UnreadByte(); err != nil {
				return 0, err
			}
			return byte, nil
		}
	}
}
