package serving

import "strings"

const DefaultAccount = "library"
const DefaultVersion = "latest"

func FormatNamedMantikId(name string, account *string, version *string) string {
	var accountToUse = DefaultAccount
	if account != nil {
		accountToUse = *account
	}
	var versionToUse = DefaultVersion
	if version != nil {
		versionToUse = *version
	}
	builder := strings.Builder{}
	if accountToUse != DefaultAccount {
		builder.WriteString(accountToUse)
		builder.WriteByte('/')
	}
	builder.WriteString(name)
	if versionToUse != DefaultVersion {
		builder.WriteByte(':')
		builder.WriteString(versionToUse)
	}
	return builder.String()
}
