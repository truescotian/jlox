package jlox;

class Interpreter implements Expr.Visitor<Object> {
	@Override
	public Object visitLiteralExpr(Expr.Literal expr) {
		// convert the literal tree node into a runtime value
		return expr.value;
	}

	@Override
	public Object visitUnaryExpr(Expr.Unary expr) {
		// we can't evaluate the unary operator until we evaluate
		// it's operand subexpression. This means the interpreter
		// is doing a post-order traversal -- each node evaluates
		// its children before doing its own work
		
		// first, evaluate the operand expression because 
		Object right = evaluate(expr.right);

		// apply the unary operator itself to the result of the operand expression
		switch (expr.operator.type) {
			case BANG:
				return !isTruthy(right);
			case MINUS:
				return -(double)right;
		}

		// Unreachable.
		return null;
	}

	@Override
	public Object visitGroupingExpr(Expr.Grouping expr) {
		// recursively evaluate the grouped (inner node within parenthesis) 
		// expression
		return evaluate(expr.expression);
	}

	// send the expression back into the interpreter's visitor implementation
	private Object evaluate(Expr expr) {
		return expr.accept(this);
	}
}
