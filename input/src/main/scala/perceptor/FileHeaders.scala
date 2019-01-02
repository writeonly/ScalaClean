// scalafix:off
/*
rule = DisableSyntax
DisableSyntax.regex = [
  {
    id = "file-headers"
    pattern = "(\\* Created by [a-z]+ on|Copyright .* KEYWORD|\\@author)"
    message = "No file headers"
  }
]
*/
/**
  * This is my file header
  * Created by gjeta on 28/12/18 // assert: DisableSyntax.file-headers
  * Copyright KEYWORD // assert: DisableSyntax.file-headers
 */
// scalafix:on
package perceptor

// currently fails because we're trying to match on a comment (and regex parser is wrong)
class FileHeaders {
  def fakeFileHeader() = List(
    "* Created by gjeta on 28/12/18", // assert: DisableSyntax.file-headers
    "Copyright 2018 KEYWORD" // assert: DisableSyntax.file-headers
  )
}
