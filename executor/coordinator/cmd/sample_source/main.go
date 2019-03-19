package main

import (
	"coordinator/cmd/sample_common"
	"net/http"
)

const Lorum = `
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas sagittis, lectus ac euismod blandit, metus erat volutpat metus, id mattis est libero et nibh. Vivamus orci orci, aliquet eu massa faucibus, hendrerit varius mauris. Quisque ut eleifend justo. Cras mattis non ipsum et facilisis. Suspendisse ut hendrerit purus. Pellentesque pharetra diam a sapien congue venenatis. Morbi fermentum nisl at tellus laoreet, nec gravida ligula eleifend. Fusce tellus ante, eleifend quis malesuada in, tempus a dolor.

Cras aliquet tincidunt fringilla. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus ut tellus nec nulla scelerisque egestas in et sapien. Etiam commodo mauris at maximus maximus. Curabitur vel pharetra ante, in volutpat ligula. Quisque condimentum nisi sed magna porttitor, ut tempor sapien feugiat. Pellentesque molestie porttitor justo et ullamcorper. Vivamus facilisis, massa eget tincidunt sodales, ligula erat malesuada dolor, ac consectetur nulla augue id metus. Maecenas malesuada id urna vitae consectetur. Integer lectus est, luctus ut placerat ac, auctor non ipsum. Suspendisse tellus urna, scelerisque ac ultrices sed, semper eu dolor. Aliquam suscipit mauris urna, quis pellentesque odio vehicula quis. Praesent velit justo, posuere bibendum ipsum nec, sagittis aliquet nibh. Pellentesque tristique suscipit sapien, ut consectetur risus condimentum at. Sed feugiat pellentesque felis, id consectetur mauris cursus ut.

Aenean a libero non nisi laoreet pretium. Interdum et malesuada fames ac ante ipsum primis in faucibus. Nunc eu facilisis enim, nec tincidunt nunc. Cras egestas eu orci nec auctor. Praesent consequat elementum congue. Mauris eu interdum nulla, vitae congue nisi. Nam eleifend metus id purus condimentum, ornare eleifend ligula sagittis. Suspendisse quis ullamcorper sapien, vitae eleifend ante. Sed rutrum diam ut lacus laoreet, vitae vestibulum mauris faucibus.

Aliquam sodales elit quis ultrices tincidunt. Sed ut tempor orci, ut lobortis nisi. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Ut rhoncus orci quis fermentum lacinia. Ut rhoncus augue id varius ullamcorper. Aliquam eu tellus et massa venenatis sagittis. Proin viverra nunc non turpis luctus condimentum. Aliquam in ex mattis, ullamcorper lorem id, ultrices ipsum. Fusce aliquet quam non turpis consectetur efficitur. Mauris vehicula in sapien consectetur consequat. Suspendisse placerat ex id ornare pretium. Praesent maximus velit non facilisis fringilla. Aenean sagittis iaculis dui, sed scelerisque nisi scelerisque non. Morbi pulvinar dictum metus, eget blandit metus tincidunt eu. Aliquam interdum placerat quam id interdum.

Sed et ullamcorper metus. Aliquam erat volutpat. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Integer facilisis tristique ipsum, ut mattis tellus. Ut vulputate, est eget dictum euismod, nisl justo semper ligula, at porta nunc dui sit amet odio. Suspendisse mattis, est a venenatis gravida, enim orci congue risus, non sagittis elit libero vitae ipsum. Quisque accumsan tellus eget nunc consequat aliquet ac quis felis. Fusce et finibus libero.

Nullam eget fringilla nibh. Mauris venenatis dui quis scelerisque tristique. Vestibulum interdum, purus quis rhoncus sagittis, ligula nulla aliquam diam, quis finibus velit augue quis ligula. Nulla varius ex sed nisl porta, vel aliquet nibh porttitor. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Fusce hendrerit ligula lorem, in finibus lorem dignissim condimentum. Duis ipsum libero, lacinia sed risus non, accumsan rhoncus neque. Aenean bibendum tincidunt finibus. Suspendisse ac mi id augue porta consequat in ac velit. Mauris in sollicitudin risus.
`

func main() {
	s := sample_common.CrateServerWithForce("sample_sink")
	s.AddGet("Sample Content", "/out", func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusOK)
		for i := 0; i < 100; i++ {
			writer.Write([]byte(Lorum))
		}
	})
	s.RunWithForce()
}
