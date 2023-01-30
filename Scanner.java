package jlox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jlox.TokenType.*;

class Scanner {
	private final String source;
	private final List<Token> tokens = new ArrayList<>();
	private int start = 0;
	private int current = 0;
	private int line = 1;

	private static final Map<String, TokenType> keywords;

	static {
		keywords = new HashMap<>();
		keywords.put("and", AND);
		keywords.put("class", CLASS);
		keywords.put("else", ELSE);
		keywords.put("false", FALSE);
		keywords.put("for", FOR);
		keywords.put("fun", FUN);
		keywords.put("if", IF);
		keywords.put("nil", NIL);
		keywords.put("or", OR);
		keywords.put("print", PRINT);
		keywords.put("return", RETURN);
		keywords.put("super", SUPER);
		keywords.put("this", THIS);
		keywords.put("true", TRUE);
		keywords.put("var", VAR);
		keywords.put("while", WHILE);
	}

	Scanner(String source) {
		this.source = source;
	}

	List<Token> scanTokens() {
		while (!isAtEnd()) {
			// We are at the beginning of the next lexeme
			start = current;
			scanToken();
		}

		tokens.add(new Token(EOF, "", null, line));
		return tokens;
	}

	private void scanToken() {
		char c = advance();
		switch (c) {
			case '(': addToken(LEFT_PAREN); break;
			case ')': addToken(RIGHT_PAREN); break;
			case '{': addToken(LEFT_BRACE); break;
			case '}': addToken(RIGHT_BRACE); break;
			case ',': addToken(COMMA); break;
			case '.': addToken(DOT); break;
			case '-': addToken(MINUS); break;
			case '+': addToken(PLUS); break;
			case ';': addToken(SEMICOLON); break;
			case '*': addToken(STAR); break;

			// when the very next chracter is something like
			// an equals sign, so "!=", then create a != lexeme.
			// Using match() we recognize these lexemes in two
			// stages. When we receive "!", jump to its switch case
			// so we know the lexeme starts with "!", then look
			// at the next character to determine if we're on 
			// a "!=" or merely a "!".
			case '!':
				  addToken(match('=') ? BANG_EQUAL : BANG);
				  break;
			case '=':
				  addToken(match('=') ? EQUAL_EQUAL : EQUAL);
				  break;
			case '<':
				  addToken(match('=') ? LESS_EQUAL : LESS);
				  break;
			case '>':
				  addToken(match('=') ? GREATER_EQUAL : GREATER);
				  break;
			case '/':
				  // For division, that character needs special
				  // handling because comments begin with a slash too.
				  // Since match() does a lookahead, this is checking 
				  // "//"
				  if (match('/')) {
					// A comment goes until the end of a line.
					// Notice that we don't call addToken(). This is
					// because comments are useless lexemes, and the
					// parser shouldn't deal with them.
					while (peek() != '\n' && !isAtEnd()) advance();
				  } else if (match('*')) {
					blockComment();
				  } else {
					addToken(SLASH);
				  }
				  break;
			case ' ':
			case '\r':
			case '\t':
				  // Ignore whitespace.
				  break;

			case '\n':
				  line++;
				  break;
			
			case '"': string(); break;


			default:
				if (isDigit(c)) {
					number();
				} else if (isAlpha(c)) {
					identifier();
				} else {
					Lox.error(line, "Unexpected character.");
				}
				break;
		}
	}

	private void identifier() {
		while (isAlphaNumeric(peek())) advance();

		String text = source.substring(start, current);
		TokenType type = keywords.get(text);
		if (type == null) type = IDENTIFIER;
		addToken(type);
	}

	// all numbers are floating point at runtime, but this supports both integers
	// and decimal literals. A number literal is a series of digits optionally
	// followed by a . and one or more trailing digits:
	// 1234
	// 12.34
	private void number() {
		while (isDigit(peek())) advance();

		if (peek() == '.' && isDigit(peekNext())) {
			advance();

			while (isDigit(peek())) advance();
		}

		addToken(NUMBER,
				Double.parseDouble(source.substring(start, current)));
	}

	private void blockComment() {
		while (peek() != '*' && peekNext() != '/' && !isAtEnd()) {
			if (peek() == '\n') line++;
			advance();	
		}

		if (isAtEnd()) {
			Lox.error(line, "Unterminated string.");
			return;
		}
		advance();
		advance();
	}

	// string will consume characters until we het the " that ends the 
	// string. Handles running out of input before the string is closed
	// and report an error for it.
	// This supports multi-line strings.
	private void string() {
		// consume until end of quote is found, or end of source.
		while (peek() != '"' && !isAtEnd()) {
			if (peek() == '\n') line++;
			advance();
		}
		// if you're here, you either reached an ", or you've reached
		// the end of source.
		
		if (isAtEnd()) { // end of source without finding "
			Lox.error(line, "Unterminated string.");
			return;
		}

		// The closing ".
		advance();

		// Trim the surrounding quotes. This value will be used later
		// by the interpreter.
		String value = source.substring(start + 1, current - 1);
		addToken(STRING, value);
	}

	// match is basically conditional advance(). Only consume the current
	// character if it's what we're looking for.
	private boolean match(char expected) {
		if (isAtEnd()) return false;
		if (source.charAt(current) != expected) return false;

		current++;
		return true;
	}

	// peek looks at the next character. If is at end, then '\0' is returned.
	// Similar to advnace(), but doesn't consume a character. This is called 
	// a lookahead.
	private char peek() {
		if (isAtEnd()) return '\0';
		return source.charAt(current);
	}

	private char peekNext() {
		if (current + 1 >= source.length()) return '\0';
		return source.charAt(current + 1);
	}

	private boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') ||
			(c >= 'A' && c <= 'Z') ||
			c == '_';
	}

	private boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}

	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private boolean isAtEnd() {
		return current >= source.length();
	}

	private char advance() {
		current++;
		return source.charAt(current - 1);
	}

	private void addToken(TokenType type) {
		addToken(type, null);	
	}

	private void addToken(TokenType type, Object literal) {
		String text = source.substring(start, current);
		tokens.add(new Token(type, text, literal, line));
	}
}
