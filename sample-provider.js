var provider = {
  name: "sample",
  label: "Sample Provider",
  // title: anime title, ep: episode number, lang: "sub" | "eng" | "hin"
  // return {url, title}, or {notice: "message"} if unavailable
  getStream: function (title, ep, lang) {
    var url = ytdlp.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    return { url: url, title: title + " - Episode " + ep };
  }
};
