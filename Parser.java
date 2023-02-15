package jlox;

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
	private final List<Token> tokens;
	private int current = 0;

	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}

	private Expr expression() {
		return equality();
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
	}

	// rule: ( "!" | "-" ) unary
	private Expr unary() {
		if (match(BANG, MINUS)) {
			Token operator = previous();
			Expr right = unary();
			return new Expr.Unary(operator, right);
		}

		return primary();
	}

	// rule: NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")"
	private Expr primary() {
		if (match(FALSE)) return new Expr.Literal(false);
		if (match(TRUE)) return new Expr.Literal(true);
		if (match(NIL)) return new Expr.Literal(null);

		if (match(NUMBER, STRING)) {
			return new Expr.Literal(previous().literal);
		}	

		if (match(LEFT_PAREN)) {
			Expr expr = expression();
			consume(RIGHT_PAREN, "Expect ')' after expression.");
			return new Expr.Grouping(expr);
		}
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
}
