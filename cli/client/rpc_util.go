package client

/* Encode optional strings into empty strings. */
func EncodeOptionalString(s *string) string {
	if s == nil {
		return ""
	} else {
		return *s
	}
}
