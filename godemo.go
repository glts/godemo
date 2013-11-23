// Simplified code for the web crawler example seen at
// http://research.swtch.com/gotour.
package main

import (
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"time"
)

var urls = map[string]string{
	"Go":      "http://golang.org/",
	"Python":  "http://python.org/",
	"Perl":    "http://www.perl.org/",
	"Scala":   "http://www.scala-lang.org/",
	"Clojure": "http://clojure.org/",
	"Haskell": "http://www.haskell.org/haskellwiki/Haskell",
	"Ruby":    "http://www.ruby-lang.org/en/",
}

func do(f func(string, string)) {
	for k, v := range urls {
		f(k, v)
	}
}

func count(name, url string, c chan<- string) {
	start := time.Now()
	r, err := http.Get(url)
	if err != nil {
		c <- fmt.Sprintf("%s: %s", name, err)
		return
	}
	n, _ := io.Copy(ioutil.Discard, r.Body)
	r.Body.Close()
	dt := time.Since(start).Seconds()
	c <- fmt.Sprintf("%s %d [%.3fs]", name, n, dt)
}

func main() {
	c := make(chan string)
	n := 0
	do(func(name, url string) {
		n++
		go count(name, url, c)
	})

	timeout := time.After(time.Second)
	for i := 0; i < n; i++ {
		select {
		case result := <-c:
			fmt.Println(result)
		case <-timeout:
			fmt.Println("Timed out")
			return
		}
	}
}
