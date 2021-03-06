package laika.directive

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import laika.parse.helper.ParseResultHelpers
import laika.parse.helper.DefaultParserHelpers
import laika.tree.helper.ModelBuilder
import laika.template.TemplateParsers
import laika.directive.Directives.Spans
import laika.directive.Directives.Spans.Directive
import laika.directive.Directives.Default
import laika.tree.Templates._
import laika.tree.Elements._
import laika.tree.Templates.MarkupContextReference
import laika.util.Builders._
import laika.parse.InlineParsers

class SpanDirectiveAPISpec extends FlatSpec
                          with ShouldMatchers
                          with ModelBuilder {

  
  object DirectiveSetup {
    import Spans.Combinators._
    import Spans.Converters._
    import laika.util.Builders._
    
    trait RequiredDefaultAttribute {
      val directive = Spans.create("dir") { attribute(Default) map (Text(_)) }
    }
    
    trait OptionalDefaultAttribute {
      val directive = Spans.create("dir") { 
        attribute(Default, positiveInt).optional map (num => Text(num.map(_.toString).getOrElse("<>"))) 
      }
    }
    
    trait RequiredNamedAttribute {
      val directive = Spans.create("dir") { attribute("name") map (Text(_)) }
    }
    
    trait OptionalNamedAttribute {
      val directive = Spans.create("dir") { 
        attribute("name", positiveInt).optional map (num => Text(num.map(_.toString).getOrElse("<>"))) 
      }
    }
    
    trait RequiredDefaultBody {
      val directive = Spans.create("dir") { body(Default) map (SpanSequence(_)) }
    }
    
    trait OptionalDefaultBody {
      val directive = Spans.create("dir") { 
        body(Default).optional map (spans => SpanSequence(spans.getOrElse(Nil))) 
      }
    }
    
    trait RequiredNamedBody {
      val directive = Spans.create("dir") { body("name") map (SpanSequence(_)) }
    }
    
    trait OptionalNamedBody {
      val directive = Spans.create("dir") { 
        body("name").optional map (spans => SpanSequence(spans.getOrElse(Nil))) 
      }
    }
    
    trait FullDirectiveSpec {
      val directive = Spans.create("dir") {
        (attribute(Default) ~ attribute("strAttr").optional ~ attribute("intAttr", positiveInt).optional ~
        body(Default) ~ body("spanBody").optional ~ body("intBody", positiveInt).optional) {
          (defAttr, strAttr, intAttr, defBody, spanBody, intBody) => 
            val sum = intAttr.getOrElse(0) + intBody.getOrElse(0)
            val str = defAttr + ":" + strAttr.getOrElse("..") + ":" + sum
            SpanSequence(Text(str) +: (defBody ++ spanBody.getOrElse(Nil)))
        }
      }
    }
    
    trait DirectiveWithParserAccess {
      val directive = Spans.create("dir") { 
        (body(Default, string) ~ parser) {
          (body, parser) => SpanSequence(parser(body.drop(3)))
        }
      }
    }
    
    trait DirectiveWithContextAccess {
      val directive = Spans.create("dir") { 
        (body(Default, string) ~ context) {
          (body, context) => Text(body + context.document.path)
        }
      }
    }
    
  }
  
  trait EmptyInlineParsers extends InlineParsers {
    override protected def prepareSpanParsers = Map[Char,Parser[Span]]()
  }
  trait SpanParser extends EmptyInlineParsers with TemplateParsers.MarkupSpans
                          with ParseResultHelpers 
                          with DefaultParserHelpers[SpanSequence] {
    
    val directive: Directive
    
    def getSpanDirective (name: String): Option[Spans.Directive] =
      if (directive.name == name) Some(directive) else None
      
    val defaultParser = spans(any,spanParsers) ^^ (SpanSequence(_))
    
    def invalid (input: String, error: String) = 
        InvalidSpan(SystemMessage(laika.tree.Elements.Error, error), Literal(input))
        
    def ss (spans: Span*) = SpanSequence(spans)
  }
  

  import DirectiveSetup._
  
  "The span directive parser" should "parse a directive with one required default string attribute" in {
    new RequiredDefaultAttribute with SpanParser {
      Parsing ("aa @:dir foo. bb") should produce (ss(txt("aa foo bb")))
    }
  }
  
  it should "detect a directive with a missing required default attribute" in {
    new RequiredDefaultAttribute with SpanParser {
      val msg = "One or more errors processing directive: required default attribute is missing"
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa "), invalid("@:dir.",msg), txt(" bb")))
    }
  }
  
  it should "parse a directive with an optional default int attribute" in {
    new OptionalDefaultAttribute with SpanParser {
      val msg = "One or more errors processing directive: required default attribute is missing"
      Parsing ("aa @:dir 5. bb") should produce (ss(txt("aa 5 bb")))
    }
  }
  
  it should "detect a directive with an optional invalid default int attribute" in {
    new OptionalDefaultAttribute with SpanParser {
      val msg = "One or more errors processing directive: Not an integer: foo"
      Parsing ("aa @:dir foo. bb") should produce (ss(txt("aa "), invalid("@:dir foo.",msg), txt(" bb")))
    }
  }
  
  it should "parse a directive with a missing optional default int attribute" in {
    new OptionalDefaultAttribute with SpanParser {
      val msg = "One or more errors processing directive: required default attribute is missing"
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa <> bb")))
    }
  }
  
  it should "parse a directive with one required named string attribute" in {
    new RequiredNamedAttribute with SpanParser {
      Parsing ("aa @:dir name=foo. bb") should produce (ss(txt("aa foo bb")))
    }
  }
  
  it should "parse a directive with a named string attribute value in quotes" in {
    new RequiredNamedAttribute with SpanParser {
      Parsing ("""aa @:dir name="foo bar". bb""") should produce (ss(txt("aa foo bar bb")))
    }
  }
  
  it should "detect a directive with a missing required named attribute" in {
    new RequiredNamedAttribute with SpanParser {
      val msg = "One or more errors processing directive: required attribute with name 'name' is missing"
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa "), invalid("@:dir.",msg), txt(" bb")))
    }
  }
  
  it should "parse a directive with an optional named int attribute" in {
    new OptionalNamedAttribute with SpanParser {
      val msg = "One or more errors processing directive: required default attribute is missing"
      Parsing ("aa @:dir name=5. bb") should produce (ss(txt("aa 5 bb")))
    }
  }
  
  it should "detect a directive with an optional invalid named int attribute" in {
    new OptionalNamedAttribute with SpanParser {
      val msg = "One or more errors processing directive: Not an integer: foo"
      Parsing ("aa @:dir name=foo. bb") should produce (ss(txt("aa "), invalid("@:dir name=foo.",msg), txt(" bb")))
    }
  }
  
  it should "parse a directive with a missing optional named int attribute" in {
    new OptionalNamedAttribute with SpanParser {
      val msg = "One or more errors processing directive: required default attribute is missing"
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa <> bb")))
    }
  }
  
  it should "parse a directive with a required default body" in {
    new RequiredDefaultBody with SpanParser {
      val body = ss(txt(" some "), MarkupContextReference("ref"), txt(" text "))
      Parsing ("aa @:dir: { some {{ref}} text } bb") should produce (ss(txt("aa "), body, txt(" bb")))
    }
  }
  
  it should "detect a directive with a missing required default body" in {
    new RequiredDefaultBody with SpanParser {
      val msg = "One or more errors processing directive: required default body is missing"
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa "), invalid("@:dir.",msg), txt(" bb")))
    }
  }
  
  it should "parse a directive with an optional default body" in {
    new OptionalDefaultBody with SpanParser {
      val body = ss(txt(" some "), MarkupContextReference("ref"), txt(" text "))
      Parsing ("aa @:dir: { some {{ref}} text } bb") should produce (ss(txt("aa "), body, txt(" bb")))
    }
  }
  
  it should "parse a directive with a missing optional default body" in {
    new OptionalDefaultBody with SpanParser {
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa "), ss(), txt(" bb")))
    }
  }
  
  it should "parse a directive with a required named body" in {
    new RequiredNamedBody with SpanParser {
      val body = ss(txt(" some "), MarkupContextReference("ref"), txt(" text "))
      Parsing ("aa @:dir: ~name: { some {{ref}} text } bb") should produce (ss(txt("aa "), body, txt(" bb")))
    }
  }
  
  it should "detect a directive with a missing required named body" in {
    new RequiredNamedBody with SpanParser {
      val msg = "One or more errors processing directive: required body with name 'name' is missing"
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa "), invalid("@:dir.",msg), txt(" bb")))
    }
  }
  
  it should "parse a directive with an optional named body" in {
    new OptionalNamedBody with SpanParser {
      val body = ss(txt(" some "), MarkupContextReference("ref"), txt(" text "))
      Parsing ("aa @:dir: ~name: { some {{ref}} text } bb") should produce (ss(txt("aa "), body, txt(" bb")))
    }
  }
  
  it should "parse a directive with a missing optional named body" in {
    new OptionalNamedBody with SpanParser {
      Parsing ("aa @:dir. bb") should produce (ss(txt("aa "), ss(), txt(" bb")))
    }
  }
  
  it should "parse a full directive spec with all elements present" in {
    new FullDirectiveSpec with SpanParser {
      val body = ss(
        txt("foo:str:16"), 
        txt(" 1 "), MarkupContextReference("ref1"), txt(" 2 "), 
        txt(" 3 "), MarkupContextReference("ref3"), txt(" 4 ")
      )
      Parsing ("aa @:dir foo strAttr=str intAttr=7: { 1 {{ref1}} 2 } ~spanBody: { 3 {{ref3}} 4 } ~intBody: { 9 } bb") should produce (ss(txt("aa "), body, txt(" bb")))
    }
  }
  
  it should "parse a full directive spec with all optional elements missing" in {
    new FullDirectiveSpec with SpanParser {
      val body = ss(
        txt("foo:..:0"), 
        txt(" 1 "), MarkupContextReference("ref1"), txt(" 2 ")
      )
      Parsing ("aa @:dir foo: { 1 {{ref1}} 2 } bb") should produce (ss(txt("aa "), body, txt(" bb")))
    }
  }
  
  it should "detect a full directive spec with all one required attribute and one required body missing" in {
    new FullDirectiveSpec with SpanParser {
      val msg = "One or more errors processing directive: required default attribute is missing, required default body is missing"
      Parsing ("aa @:dir strAttr=str. bb") should produce (ss(txt("aa "), invalid("@:dir strAttr=str.",msg), txt(" bb")))
    }
  }
  
  it should "parse a directive with a required default body and parser access" in {
    new DirectiveWithParserAccess with SpanParser {
      val body = ss(txt("me "), MarkupContextReference("ref"), txt(" text "))
      Parsing ("aa @:dir: { some {{ref}} text } bb") should produce (ss(txt("aa "), body, txt(" bb")))
    }
  }
  
  it should "parse a directive with a required default body and context access" in {
    new DirectiveWithContextAccess with SpanParser {
      def translate (result: SpanSequence) = result rewrite {
        case d: DirectiveSpan => Some(Text("ok")) // cannot compare DirectiveSpans
      }
      Parsing ("aa @:dir: { text } bb") map translate should produce (ss(txt("aa "), txt("ok"), txt(" bb")))
    }
  }
  
  
  
  it should "detect a directive with an unknown name" in {
    new OptionalNamedAttribute with SpanParser {
      val msg = "One or more errors processing directive: No span directive registered with name: foo"
      Parsing ("aa @:foo name=foo. bb") should produce (ss(txt("aa "), invalid("@:foo name=foo.",msg), txt(" bb")))
    }
  }
  
  
}