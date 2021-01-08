package shark

import com.apurebase.kgraphql.KGraphQL

data class Article(val id: Int, val text: String)

fun main() {
  val schema = KGraphQL.schema {
    query("article") {
      resolver { id: Int?, text: String ->
        Article(id ?: -1, text)
      }
    }
    type<Article> {
      property<String>("fullText") {
        resolver { article: Article ->
          "${article.id}: ${article.text}"
        }
      }
    }
  }

  schema.executeBlocking("""
        {
            article(id: 5, text: "Hello World") {
                id
                fullText
            }
        }
    """.trimIndent()).let(::println)
}