package com.ezanmoto.apl

import com.ezanmoto.apl.Type._

class APLInterpreter {

  private var env = Map[String, Variable]()

  private var in: LookaheadStream = new LookaheadStream( "" )

  var isRunning = true

  def interpret( line: String ): Unit = {
    this.in = new LookaheadStream( line )
    interpret(): Unit
  }

  def interpret(): Unit = {
    in.skipWhitespace()
    in.peek match {
      case Uppercase( _ ) => assignmentOrExpression()
      case '(' | '\'' | '~' | Integer( _ ) | 'i' | 'p' | '+' => expression()
      case ':' => command()
    }
    in.skipWhitespace()
    if ( ! in.isEmpty ) error( "Trailing characters" )
  }

  def assignmentOrExpression() = {
    val name = readName()
    in.skipWhitespace()
    val index = 
      if ( ! in.isEmpty && in.peek == '[' ) Some( readIndex() ) else None
    in.skipWhitespace()
    if ( in.isEmpty ) { // Print value
      var value: Variable = lookup( name )
      if ( None != index )
        value = value at index.get
      println( value )
    } else if ( in.peek == ':' ) { // Assignment
      in.eat( ':' )
      in.skipWhitespace()
      if ( index == None )
        env = env + ( name -> expression() )
      else
        env = env + ( name -> indexAssignment( lookup( name ), index get ) )
    } else { // Start of expression
      var value: Variable = lookup( name )
      if ( None != index )
        value = value at index.get
      println( expressionAfter( value ) )
    }
  }

  def readName(): String = {
    in.skipWhitespace()
    if ( in.isEmpty || ( ! in.peek.isUpper ) )
      error( "Expected identifier" )
    else {
      var buffer = ""
      do {
        buffer = buffer + in.peek
        in.skip()
      } while ( ! in.isEmpty && ( in.peek isUpper ) )
      buffer
    }
  }

  def expression() = expressionAfter( value() )

  def value(): Variable = {
    in.skipWhitespace()
    if ( in.isEmpty )
      error( "Expected '~', identifier or integer" )
    else
      in.peek match {
        case '(' => {
          in.eat( '(' )
          val e = expression()
          in.eat( ')' )
          expressionAfter( e )
        }
        case '\'' => string()
        case Uppercase( _ ) => variable()
        case '~' | Integer( _ ) => integerOrListAfter( signedInteger() )
        case _ => unaryFunction()
      }
  }

  def unaryFunction(): Variable = in.peek match {
    case 'i' => in.eat( 'i'  ); expression() interval
    case 'p' => in.eat( 'p'  ); Variable( expression() length )
    case '+' => in.eat( "+/" ); expression() sum
  }

  def integerOrListAfter( integer: Int ): Variable = {
    in.skipWhitespace()
    if ( in.isEmpty && ! in.peek.isDigit && in.peek != '~' )
      Variable( integer )
    else {
      var list = integer :: Nil
      do {
        list = list ::: List( signedInteger() )
        in.skipWhitespace()
      } while ( ! in.isEmpty && ( in.peek.isDigit || in.peek == '~' ) )
      Variable( list )
    }

  def signedInteger(): Int = {
    in.skipWhitespace()
    if ( in.isEmpty )
      error( "Expected integer or '~'" )
    else {
      var isNegative = in.peek == '~'
      if ( isNegative ) in.eat( '~' )
      integer() * ( if ( isNegative ) -1 else 1 )
    }
  }

  def integer(): Int = {
    in.skipWhitespace()
    if ( in.isEmpty )
      error( "Expected further input" )
    else if ( ! in.peek.isDigit )
      error( "Expected integer, got '" + in.peek + "'" )
    else {
      var buffer = ""
      do {
        buffer = buffer + in.peek
        in.skip()
      } while ( ! in.isEmpty && in.peek.isDigit )
      buffer.toInt;
    }
  }

  def variable() = {
    var value = lookup( readName() )
  }

  def expressionAfter( value: Variable ) = {
    in.skipWhitespace()
    if ( in.isEmpty )
      value
    else
      arithmetic( value )
  }

  def arithmetic( a: Variable ): Variable = in.peek match {
    case '+' => in.eat( '+' ); expressionAfter( a + expression() )
    case '-' => in.eat( '-' ); expressionAfter( a - expression() )
    case 'x' => in.eat( 'x' ); expressionAfter( a * expression() )
    case '%' => in.eat( '%' ); expressionAfter( a / expression() )
    case '|' => in.eat( '|' ); expressionAfter( a % expression() )
    case _   => concatenation( a )
  }

  def concatenation( a: Variable ): Variable = in.peek match {
    case ',' => in.eat( ',' ); expressionAfter( a ++ expression() )
    case _   => comparison( a )
  }

  def comparison( a: Variable ): Variable = in.peek match {
    case '=' => in eat '='; expressionAfter( a == expression() )
    case 'n' => in eat 'n'; expressionAfter( a != expression() )
    case '<' => in eat '<'; expressionAfter( a <  expression() )
    case 'l' => in eat 'l'; expressionAfter( a <= expression() )
    case '>' => in eat '>'; expressionAfter( a >  expression() )
    case _   => minimax( a )
  }

  def minimax( a: Variable ): Variable = in.peek match {
    case 'r' => in eat 'r'; expressionAfter( a max expression() )
    case '_' => in eat '_'; expressionAfter( a min expression() )
    case _   => unexpected()
  }



  def interpret(): Unit = {
    in.skipWhitespace()
    in.peek match {
      case '\'' | '~' | Integer( _ ) | 'i' | 'p' | '+' | '(' =>
        println( expression() )
      case ':'  => runCommand()
      case Uppercase( _ ) => assignment()
      case _    => unexpected()
    }
    in.skipWhitespace()
    if ( ! in.isEmpty )
      error( "Trailing characters" )
  }

  def unexpected() =
    if ( in.isEmpty )
      error( "Unexpected end of line" )
    else
      error( "Unexpected '" + in.peek + "'" )

  def readString(): String = {
    in eat '\''
    var buffer = ""
    while ( ! in.isEmpty && in.peek != '\'' )
      buffer = buffer + in.drop
    if ( in.isEmpty )
      error( "Expected string terminator" )
    else {
      in eat '\''
      buffer
    }
  }

  def error( s: String ) = throw new RuntimeException( "[!] Error: " + s )

  def runCommand(): Unit = {
    in eat ':'
    if ( in.isEmpty )
      error( "Expected further input" )
    else
      in.peek match {
        case 'q' => in eat 'q'; isRunning = false; println( "Goodbye." )
        case _   => unexpected()
      }
  }

  def expression(): Variable = {
    in.skipWhitespace()
    if ( '(' == in.peek ) {
      in eat '('
      val e = expressionAfter( expression() )
      in.skipWhitespace()
      in eat ')'
      expressionAfter( e )
    } else
      expressionAfter( readValue() )
  }

  def expressionAfter( a: Variable ): Variable = {
    in.skipWhitespace()
    if ( in.isEmpty )
      a
    else
      in.peek match {
        case '+' => in eat '+'; expressionAfter( a +  expression() )
        case '-' => in eat '-'; expressionAfter( a -  expression() )
        case 'x' => in eat 'x'; expressionAfter( a *  expression() )
        case '%' => in eat '%'; expressionAfter( a /  expression() )
        case '|' => in eat '|'; expressionAfter( a %  expression() )
        case ',' => in eat ','; expressionAfter( a ++ expression() )
        case '[' => expressionAfter( a at readIndex() )
        case '=' => in eat '='; expressionAfter( a == expression() )
        case 'n' => in eat 'n'; expressionAfter( a != expression() )
        case '<' => in eat '<'; expressionAfter( a <  expression() )
        case 'l' => in eat 'l'; expressionAfter( a <= expression() )
        case '>' => in eat '>'; expressionAfter( a >  expression() )
        case 'g' => in eat 'g'; expressionAfter( a >= expression() )
        case 'r' => in eat 'r'; expressionAfter( a max expression() )
        case '_' => in eat '_'; expressionAfter( a min expression() )
        case Integer( _ ) | '~' =>
          if ( a.isInteger )
            expressionAfter( Variable( readListAfter( a integerValue ) ) )
          else {
            unexpected()
          }
        case '(' => expressionAfter( expression() )
        case _   => unexpected()
      }
  }

  def readIndex(): Variable = {
    in eat '['
    val i = expression()
    in.skipWhitespace()
    in eat ']'
    i
  }

  def readListAfter( a: Int ): List[Int] = {
    in.skipWhitespace()
    if ( in.isEmpty )
      unexpected()
    else {
      var list = a :: Nil
      while ( ! in.isEmpty && in.peek.isDigit ) {
        list = list ::: List( readSignedInteger() )
        in.skipWhitespace()
      }
      list
    }
  }

  def readIntegerOrList(): Variable = {
    val a = readSignedInteger()
    in.skipWhitespace()
    if ( in.isEmpty )
      Variable( a )
    else {
      var list = a :: Nil
      while ( ! in.isEmpty && in.peek.isDigit ) {
        list = list ::: List( readSignedInteger() )
        in.skipWhitespace()
      }
      if ( list.length > 1 ) Variable( list ) else Variable( list head ) 
    }
  }

  def readValue(): Variable = {
    in.skipWhitespace()
    if ( in.isEmpty )
      error( "Expected '~', identifier or integer" )
    else
      in.peek match {
        case '\'' => Variable( readString() )
        case Uppercase( _ ) => {
          var value = lookup( readName() )
          in.skipWhitespace()
          if ( ! in.isEmpty && in.peek == '[' )
            value at readIndex()
          else
            value
        }
        case Integer( _ ) | '~' => readIntegerOrList()
        case 'i' => interval()
        case 'p' => length()
        case '+' => sum()
        case _ => error( "Expected '~', identifier, integer or string" )
      }
  }

  def readSignedInteger(): Int = {
    in.skipWhitespace()
    if ( in.isEmpty )
      error( "Expected integer or '~'" )
    else {
      var isNegative = false
      if ( in.peek == '~' ) {
        isNegative = true
        in eat '~'
      }
      readInteger() * ( if ( isNegative ) -1 else 1 )
    }
  }

  def readInteger(): Int = {
    in.skipWhitespace()
    if ( in.isEmpty )
      error( "Expected further input" )
    else if ( ! in.peek.isDigit )
      error( "Expected integer, got '" + in.peek + "'" )
    else {
      var buffer = ""
      do {
        buffer = buffer + in.peek
        in.skip()
      } while ( ! in.isEmpty && in.peek.isDigit )
      buffer.toInt;
    }
  }

  def assignment(): Unit = {
    val name = readName()
    in.skipWhitespace()
    val index =
      if ( ! in.isEmpty && in.peek == '[' ) Some( readIndex() ) else None
    in.skipWhitespace()
    if ( in.isEmpty ) { // Print value
      var value: Variable = lookup( name )
      if ( None != index )
        value = value at index.get
      println( value )
    } else if ( in.peek == ':' ) { // Assignment
      in.eat( ':' )
      in.skipWhitespace()
      if ( index == None )
        env = env + ( name -> expression() )
      else if ( lookup( name ) isInteger )
        error( "cannot index integer" )
      else
        env = env + ( name -> indexAssignment( lookup( name ), index get ) )
    } else { // Start of expression
      var value: Variable = lookup( name )
      if ( None != index )
        value = value at index.get
      println( expressionAfter( value ) )
    }
  }

  def interval(): Variable =
    if ( in.peek == 'i' ) {
      in eat 'i'
      in.skipWhitespace()
      var l: List[Int] = Nil
      for ( i <- 1 to readInteger() )
        l = l ::: List( i )
      Variable( l )
    } else
      unexpected()

  def length(): Variable = {
    in eat 'p'
    in.skipWhitespace()
    Variable( readValue() length )
  }

  def sum(): Variable = {
    in eat "+/"
    in.skipWhitespace()
    readValue() sum
  }

  def indexAssignment( lhs: Variable, index: Variable ): Variable =
    lhs replace ( index, readValue() )

  def lookup( name: String ): Variable = ( env get name ) match {
    case Some( x ) => x
    case None      => error( "'" + name + "' has not been declared" )
  }
}
