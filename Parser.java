package jlox;

import java.util.List;

import static jlox.TokenType.*;

class Parser {
	private final List<Token> tokens;
	private int current = 0;

	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}

	private Expr expression() {
		return equality();
	}

	// comparison ( ( "!=" | "==" ) comparison )*
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
		return token.get(current -1);
	}
}
