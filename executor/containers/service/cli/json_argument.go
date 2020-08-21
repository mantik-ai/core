package cli

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"github.com/pkg/errors"
	"io/ioutil"
	"os"
	"strings"
)

/**
Parse a json file which was given via argument.
3 cases
- Argument given as JSON: Directly parse it
- Argument is empty: Try to load it from environment variable env_fallback
- Argument starts with '@' load the file and parse it as JSON.

The JSON can be wrapped in Base64.
*/
func ParseJsonFile(argumentName string, argumentValue string, envFallback string, target interface{}) error {
	var content []byte
	var err error
	if len(argumentValue) == 0 {
		fallbackValue := os.Getenv(envFallback)
		if len(fallbackValue) == 0 {
			return errors.Errorf("Neither argument %s given nor environment fallback %s", argumentName, envFallback)
		}
		content = []byte(fallbackValue)
	} else {
		if strings.HasPrefix(argumentValue, "@") {
			fileName := strings.TrimPrefix(argumentValue, "@")
			content, err = ioutil.ReadFile(fileName)
			if err != nil {
				return errors.Errorf("Could not read %s", fileName)
			}
		} else {
			content = []byte(argumentValue)
		}
	}
	return tryParseJson(content, target)
}

func tryParseJson(content []byte, target interface{}) error {
	err := json.Unmarshal(content, target)
	if err == nil {
		// all fine
		return nil
	}
	decoder := base64.NewDecoder(base64.StdEncoding, bytes.NewBuffer(content))
	parsed, berr := ioutil.ReadAll(decoder)
	if berr != nil {
		return errors.Wrap(err, "Parsing error (base64 also failed)")
	}
	err = json.Unmarshal(parsed, target)
	if err != nil {
		return errors.Wrap(err, "Parsin error (base64)")
	}
	return nil
}
