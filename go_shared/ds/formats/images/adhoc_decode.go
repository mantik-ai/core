package images

import (
	"bytes"
	"encoding/binary"
	"github.com/nfnt/resize"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"io"
)

// Note: experimental code
// It also doesn't look completely working

// Tries to adhoc decode an image.
func AdhocDecode(expected *ds.Image, reader io.Reader) (*element.ImageElement, error) {
	image, _, err := image.Decode(reader)
	if err != nil {
		return nil, err
	}
	return Convert(expected, image)
}

func Convert(expected *ds.Image, src image.Image) (*element.ImageElement, error) {
	var resized image.Image
	ob := src.Bounds()
	if ob.Min.Eq(image.Point{0, 0}) && ob.Dx() == expected.Width && ob.Dy() == expected.Height {
		resized = src
	} else {
		resized = resize.Resize(uint(expected.Width), uint(expected.Height), src, resize.Bicubic)
	}
	return ConvertColorModel(expected, resized)
}

func ConvertColorModel(expected *ds.Image, src image.Image) (*element.ImageElement, error) {
	if expected.Width != src.Bounds().Dx() && expected.Height != src.Bounds().Dy() {
		panic("Expected width / height is not satisfied by source image, resize first")
	}
	if expected.Format != nil {
		// Use go's conversion routines directly
		return convertImageFormatDirectly(*expected.Format, expected, src)
	} else {
		return decodeImageIntoRaw(expected, src)
	}
}

func decodeImageIntoRaw(expected *ds.Image, src image.Image) (*element.ImageElement, error) {
	icv, err := createImageColorConverter(expected)
	if err != nil {
		return nil, err
	}
	buf := bytes.Buffer{}

	for y := 0; y < expected.Height; y++ {
		for x := 0; x < expected.Width; x++ {
			icv(src.At(x, y), &buf)
		}
	}
	result := element.ImageElement{buf.Bytes()}
	return &result, nil
}

type imageColorConverter = func(color.Color, io.Writer)

func createImageColorConverter(expected *ds.Image) (imageColorConverter, error) {
	var channelReader []imageColorConverter
	for _, c := range expected.Components {
		subReader, err := convertImageComponent(c.Channel, c.Component)
		if err != nil {
			return nil, err
		}
		channelReader = append(channelReader, subReader)

	}
	return combinedImageColorConverter(channelReader), nil
}

func combinedImageColorConverter(converters []imageColorConverter) imageColorConverter {
	return func(c color.Color, w io.Writer) {
		for _, converter := range converters {
			converter(c, w)
		}
	}
}

func convertImageComponent(channel ds.ImageChannel, component ds.ImageComponent) (imageColorConverter, error) {
	underlying, ok := component.ComponentType.Underlying.(*ds.FundamentalType)
	if !ok {
		return nil, errors.Errorf("Unsupported type %s", component.ComponentType.Underlying.TypeName())
	}
	fetcher := findColorFetcher(channel)
	writer, err := findColorWriter(underlying)
	if err != nil {
		return nil, err
	}
	var converter imageColorConverter = func(i color.Color, w io.Writer) {
		fetched := fetcher(i)
		writer(fetched, w)
	}
	return converter, nil
}

/* Returns the color as 32 bit float normalized. */
type colorFetcher = func(color.Color) float32

func findColorFetcher(channel ds.ImageChannel) colorFetcher {
	switch channel {
	case ds.Black:
		// Note: Black is inverted!
		// We have to revise this again.
		return func(i color.Color) float32 {
			// note: we are getting 16 bit values
			r, g, b, _ := i.RGBA()
			lum := 0.299*float32(r) + 0.587*float32(g) + 0.114*float32(b)
			if lum < 0 {
				return 1.0
			} else if lum > 65535.0 {
				return 0.0
			} else {
				return 1.0 - lum/65535.0
			}
		}
	case ds.Red:
		return func(i color.Color) float32 {
			r, _, _, _ := i.RGBA()
			return float32(r) / 65535.0
		}
	case ds.Blue:
		return func(i color.Color) float32 {
			_, _, b, _ := i.RGBA()
			return float32(b) / 65535.0
		}
	case ds.Green:
		return func(i color.Color) float32 {
			_, g, _, _ := i.RGBA()
			return float32(g) / 65535.0
		}
	default:
		panic("Unknown component")
		return nil
	}
}

type colorWriter = func(float32, io.Writer)

func findColorWriter(ft *ds.FundamentalType) (colorWriter, error) {
	var result colorWriter
	switch ft {
	case ds.Float32:
		result = func(f float32, writer io.Writer) {
			binary.Write(writer, binary.BigEndian, f)
		}
	case ds.Float64:
		result = func(f float32, writer io.Writer) {
			f64 := float64(f)
			binary.Write(writer, binary.BigEndian, f64)
		}
	case ds.Uint8:
		result = func(f float32, writer io.Writer) {
			u8 := uint8(f * 255.0)
			writer.Write([]byte{u8})
		}
	default:
		return nil, errors.Errorf("Unsupported type %s", ft.TypeName())
	}
	return result, nil
}

// Convert the image into the given format directly
// Color mode is not converted, as the image formats do not support them all
func convertImageFormatDirectly(formatName string, expected *ds.Image, src image.Image) (*element.ImageElement, error) {
	var data = bytes.Buffer{}
	var err error
	if formatName == "png" {
		err = png.Encode(&data, src)
	} else if formatName == "jpeg" {
		err = jpeg.Encode(&data, src, nil)
	} else {
		return nil, errors.Errorf("Format %s not supported", formatName)
	}
	if err != nil {
		return nil, err
	}
	return &element.ImageElement{
		data.Bytes(),
	}, nil
}
