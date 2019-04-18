package serving

// A Backend for regular Algorithms
type Backend interface {
	// Load a model, it's not necessary to adapt to target type yet.
	// the first in the result tuple contains backend specific data for analysis result.
	LoadModel(directory string, mantikfile *Mantikfile) (Executable, error)
}
