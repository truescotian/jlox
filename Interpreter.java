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

	@Override
	public Object visitBinaryExpr(Expr.Binary expr) {
		Object left = evaluate(expr.left);
		Object right = evaluate(expr.right);

		switch (expr.operator.type) {
			case BANG_EQUAL: return !isEqual(left, right);
			case EQUAL_EQUAL: return isEqual(left, right);
			case GREATER:
				return (double)left > (double)right;
			case GREATER_EQUAL:
				return (double)left >= (double)right;
			case LESS:
				return (double)left < (double)right;
			case LESS_EQUAL:
				return (double)left <= (double)right;
			case MINUS:
				return (double)left - (double)right;
			case PLUS:
				if (left instanceof Double && right instanceof Double) {
					return (double)left + (double)right;
				}

				if (left instanceof String && right instanceof String) {
					return (String)left + (String)right;
				}

				break;
			case SLASH:
				return (double)left / (double)right;
			case STAR:
				return (double)left * (double)right;
		}

		// Unreachable
		return null;
	}

	private boolean isTruthy(Object object) {
		if (object == null) return false;
		if (object instanceof Boolean) return (boolean)object;
		return true;
	}

	private boolean isEqual(Object a, Object b) {
		if (a == null && b == null) return true;
		if (a == null) return false;

		return a.equals(b);
	}
}
