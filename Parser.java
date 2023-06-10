package jlox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jlox.TokenType.*;

/**
 *  A parser has two jobs:
 *  1. Given a valid sequence of tokens, produce a corresponding syntax tree.
 *  2. Given an invalid sequence of tokens, detect any errors and tell 
 *  the user about their mistakes.
 *
 *  #2 is very important. In modern IDEs, the parser is always reparsing the code
 *  even while you're editing it in order to syntax highlight and support things
 *  like auto-complete. When the user doesn't know syntax is wrong, it's up to
 *  the parser to help guide them back onto the right path.
 */

class Parser {
	private static class ParseError extends RuntimeException {}

	private final List<Token> tokens;
	private int current = 0;

	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}

	List<Stmt> parse() {
		List<Stmt> statements = new ArrayList<>();
		while (!isAtEnd()) {
			statements.add(declaration());
		}

		return statements;
	}

	private Expr expression() {
		return assignment();
	}

	// most of the code for parsing an assignment expression
	// looks similiar to the other binary operators like +. We
	// parse the left-hand side, which can be any expression
	// of high precedence. If we find an =, we parse the right-hand
	// side and then wrap it all up in an assignment expression
	// tree node.
	private Expr assignment() {
		Expr expr = or();

		if (match(EQUAL)) {
			// here you are now parsing something like `var a =`
			Token equals = previous();
			Expr value = assignment();

			if (expr instanceof Expr.Variable) {
				Token name = ((Expr.Variable)expr).name;
				return new Expr.Assign(name, value);
			}

			error(equals, "Invalid assignment target.");
		}

		return expr;
	}

	// parses a series of "or" expressions.
	// it's operands are the next higher level of precedence, the "and" expression.
	// rule: logic_and ( "or" logic_and )* ;
	private Expr or() {
		Expr expr = and();

		while (match(OR)) { 
			Token operator = previous();
			Expr right = and();
			expr = new Expr.Logical(expr, operator, right);
		}

		return expr;
	}

	// parses a series of "and" expressions
	// rule: equality ( "and" equality )* ;
	private Expr and() {
		Expr expr = equality(); 

		while (match(AND)) {
			Token operator = previous();
			Expr right = equality();
			expr = new Expr.Logical(expr, operator, right);
		}

		return expr;
	}

	private Stmt declaration() {
		try {
			if (match(CLASS)) return classDeclaration();
			if (match(FUN)) return function("function");
			if (match(VAR)) return varDeclaration();

			return statement();
		} catch (ParseError error) {
			synchronize();
			return null;
		}
	}

	private Stmt classDeclaration() {
    Token name = consume(IDENTIFIER, "Expect class name.");
    consume(LEFT_BRACE, "Expect '{' before class body.");

    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"));
    }

    consume(RIGHT_BRACE, "Expect '}' after class body.");

    return new Stmt.Class(name, methods);
  }

	private Stmt statement() {
		if (match(FOR)) return forStatement();
		if (match(IF)) return ifStatement();
		if (match(PRINT)) return printStatement();
		if (match(RETURN)) return returnStatement();
		if (match(WHILE)) return whileStatement();
		if (match(LEFT_BRACE)) return new Stmt.Block(block());

		return expressionStatement();
	}

	private Stmt forStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'for'.");

		Stmt initializer;
		if (match(SEMICOLON)) {
			initializer = null;
		} else if (match(VAR)) {
			initializer = varDeclaration();
		} else {
			initializer = expressionStatement();
		}

		Expr condition = null;
		if (!check(SEMICOLON)) {
			condition = expression();
		}
		consume(SEMICOLON, "Expect ';' after loop condition.");

		Expr increment = null;
		if (!check(RIGHT_PAREN)) {
			increment = expression();
		}
		consume(RIGHT_PAREN, "Expect ')' after for clauses");
		Stmt body = statement();

		// the body, if there is one, executes after the body in each iteration of the
		// loop. We do that by replacing the body with a little block that contains the original
		// body followed by an expression statement that evaluates the increment.
		if (increment != null) {
			body = new Stmt.Block(
				Arrays.asList(body, new Stmt.Expression(increment)));
		}

		// take the condition and the body and build the loop using a primitive while loop.
		// If the condition is omitted, we jam in true to make an infinite loop.
		if (condition == null) condition = new Expr.Literal(true);
		body = new Stmt.While(condition, body);
		
		// if there is an initializer, it runs once before the entire loop. We do that
		// by, again, replacing the whole statement with a block that runs
		// the initializer and then executes the loop.
		if (initializer != null) {
			body = new Stmt.Block(Arrays.asList(initializer, body));
		}

		return body;
	}

	private Stmt ifStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'if'.");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after if condition.");

		Stmt thenBranch = statement();
		Stmt elseBranch = null;
		if (match(ELSE)) {
			elseBranch = statement();
		}

		return new Stmt.If(condition, thenBranch, elseBranch);
	}

	private Stmt printStatement() {
		Expr value = expression();
		consume(SEMICOLON, "Expect ';' after value.");
		return new Stmt.Print(value);
	}

	private Stmt returnStatement() {
		Token keyword = previous();
		Expr value = null;
		if (!check(SEMICOLON)) {
			value = expression();
		}

		consume(SEMICOLON, "Expect ';' after return value.");
		return new Stmt.Return(keyword, value);
	}

	private Stmt varDeclaration() {
		Token name = consume(IDENTIFIER, "Expect variable name.");

		Expr initializer = null;
		if (match(EQUAL)) {
			initializer = expression();
		}

		consume(SEMICOLON, "Expect ';' after variable declaration");
		return new Stmt.Var(name, initializer);
	}

	private Stmt whileStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'while'.");
		Expr condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after condition");
		Stmt body = statement();

		return new Stmt.While(condition, body);
	}

	private Stmt expressionStatement() {
		Expr value = expression();
		consume(SEMICOLON, "Expect ';' after value.");
		return new Stmt.Expression(value);
	}

	// arg king is meant to differentiate "function" from "method" where the 
	// latter is in a class.
	private Stmt.Function function(String kind) {
		Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
		consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
		List<Token> parameters = new ArrayList<>();
		if (!check(RIGHT_PAREN)) {
			do {
				if (parameters.size() >= 255) {
					error(peek(), "Can't have more than 255 parameters");
				}

				parameters.add(
					consume(IDENTIFIER, "Expect parameter name."));
			} while (match(COMMA));
		}
		consume(RIGHT_PAREN, "Expect ')' after parameters.");

		consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
		List<Stmt> body = block();
		return new Stmt.Function(name, parameters, body);
	}

	private List<Stmt> block() {
		List<Stmt> statements = new ArrayList<>();

		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			statements.add(declaration());
		}

		consume(RIGHT_BRACE, "Expect '}' after block.");
		return statements;
	}

	// rule: comparison ( ( "!=" | "==" ) comparison )*
	// this created a left-associative nested tree of binary
	// operator nodes. This is because given a == b == c == d == e
	// we zip through the sequence of equality expressions til the end
	// "e"
	private Expr equality() {

		// the first comparison nonterminal translates 
		// to a call to comparison()
		Expr expr = comparison();

		// this is the ( ... )* loop in the rule. We exit
		// the loop when we stop seeing != or == token.
		while (match(BANG_EQUAL, EQUAL_EQUAL)) {
			// we have found a != or == and must be parsing an
			// equality expression.
			
			// operator is != or ==, AKA which operator expression
			// we have.
			Token operator = previous(); 

			// parse the right-hand operand.
			Expr right = comparison();

			// Combine the operator and its two operands
			// into a new Expr.Binary syntax tree node,
			// then loop around.
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	// identical to equality() except for the token types for the operators
	// and the methods for the operands -- now term() instead of comparison()
	private Expr comparison() {
		Expr expr = term();

		while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			Token operator = previous();
			Expr right = term();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	// rule: factor ( ( "-" | "+" ) factor )*
	private Expr term() {
		Expr expr = factor();

		while (match(MINUS, PLUS)) {
			Token operator = previous();
			Expr right = factor();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	// unary ( ( "/" | "*" ) factor )*
	private Expr factor() {
		Expr expr = unary();

		while (match(SLASH, STAR)) {
			Token operator = previous();
			Expr right = unary();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	// rule: ( "!" | "-" ) unary
	private Expr unary() {
		if (match(BANG, MINUS)) {
			Token operator = previous();
			Expr right = unary();
			return new Expr.Unary(operator, right);
		}

		return call();
	}

	private Expr finishCall(Expr callee) {
		List<Expr> arguments = new ArrayList<>();
		if (!check(RIGHT_PAREN)) {
			do {
				if (arguments.size() > 255) {
					error(peek(), "Can't have more than 255 arguments");
				}
				arguments.add(expression());
			} while (match(COMMA));
		}

		Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments'");

		return new Expr.Call(callee, paren, arguments);
	}

	private Expr call() {
		Expr expr = primary();

		while (true) {
			if (match(LEFT_PAREN)) {
				expr = finishCall(expr);
			} else {
				break;
			}
		}

		return expr;
	}

	// rule: NUMBER | STRING | "true" | "false" | "nil" 
	// | "(" expression ")"
	// | IDENTIFIER;
	// Error productions...
	// | ( "!=" | "==" ) equality
	// | ( ">" | ">=" | "<" | "<=" ) comparison
	// | ( "+" ) term
	// | ( "/" | "*" ) factor 
	private Expr primary() {
		if (match(FALSE)) return new Expr.Literal(false);
		if (match(TRUE)) return new Expr.Literal(true);
		if (match(NIL)) return new Expr.Literal(null);

		if (match(NUMBER, STRING)) {
			return new Expr.Literal(previous().literal);
		}	

		if (match(IDENTIFIER)) {
			return new Expr.Variable(previous());
		}

		if (match(LEFT_PAREN)) {
			Expr expr = expression();
			consume(RIGHT_PAREN, "Expect ')' after expression.");
			return new Expr.Grouping(expr);
		}

		// Error productions
		if (match(BANG_EQUAL, EQUAL_EQUAL)) {
			error(previous(), "Missing left-hand operand.");
			equality();
			return null;
		}

		if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			error(previous(), "Missing left-hand operand.");
			comparison();
			return null;
		}

		if (match(PLUS)) {
			error(previous(), "Missing left-hand operand.");
			term();
			return null;
		}

		if (match(SLASH, STAR)) {
			error(previous(), "Missing left-hand operand.");
			factor();
			return null;
		}

		throw error(peek(), "Expect expression.");
	}	

	// Checks to see if the current token has any of the given types.
	// If so, consume the token and return true, otherwise return
	// false and leave the current token alone.
	private boolean match(TokenType... types) {
		for (TokenType type : types) {
			if (check(type)) {
				advance();
				return true;
			}
		}

		return false;
	}

	private Token consume(TokenType type, String message) {
		if (check(type)) return advance();

		throw error(peek(), message);
	}

	// returns true if the current token is of the given type.
	// Unlike match(), it never consumes the token, only looks at it.
	private boolean check(TokenType type) {
		if (isAtEnd()) return false;
		return peek().type == type;
	}

	// consumes the current token and returns it, similar to how
	// our scanner's corresponding method crawled through characters.
	private Token advance() {
		if (!isAtEnd()) current++;
		return previous();
	}

	private boolean isAtEnd() {
		return peek().type == EOF;
	}

	private Token peek() {
		return tokens.get(current);
	}

	// returns the most recently consumed token
	private Token previous() {
		return tokens.get(current -1);
	}

	private ParseError error(Token token, String message) {
		Lox.error(token, message);	
		return new ParseError();
	}

	// discard tokens until it thinks it found a statement boundary. After
	// catching a ParseError, we'll call this to get back in sync.
	private void synchronize() {
		// we want to discard tokens until we're right at the
		// beginning of the next statement.
		advance();

		while (!isAtEnd()) {
			// if true, we're probably finished with a statement.
			// return because we're all synced up and ready to parse
			// the next statement!
			if (previous().type == SEMICOLON) return;
		
			// most statements start with one of these keywords.
			// When the next (peek()) token is any one of those,
			// we're about to start a statement.
			// return because we're all synced up and ready to parse
			// the next statement!
			switch (peek().type) {
				case CLASS:
				case FUN:
				case VAR:
				case FOR:
				case IF:
				case WHILE:
				case PRINT:
				case RETURN:
					return;
			}

			advance();
		}
	}
}
