package osext

import "os"

/* Returns true if a file exists. */
func FileExists(path string) bool {
	_, err := os.Stat(path)
	return !os.IsNotExist(err)
}

/* Returns true if a path is a directory. */
func IsDirectory(path string) bool {
	s, err := os.Stat(path)
	return err == nil && s.IsDir()
}