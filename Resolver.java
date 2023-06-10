package jlox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt);
    return null;
  }

  @Override
  // variable declarations write to scope maps.
  public Void visitVarStmt(Stmt.Var stmt) {
    // we need to split binding into two steps, the first is
    // declaring which will mark it as "not ready yet" by binding
    // the name to false.
    declare(stmt.name);
    if (stmt.initializer != null) {
      // resolve the initializer expression in this scope
      // where the new variable now exists but is unavailable.
      resolve(stmt.initializer);
    }

    // ready for prime time baby. Now we define the variable
    // (it's ready)
    define(stmt.name);
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    // resolve the expression for the assigned value in case it also
    // contains references to other variables.
    resolve(expr.value);
    // resolve the variable that's being assigned to.
    resolveLocal(expr, expr.name);
    return null;
  }

  @Override
  // scope maps are read when resolving variable expressions.
  public Void visitVariableExpr(Expr.Variable expr) {
    // check to see if the variable is being accessed inside its own initializer.
    // such as var a = (a + 1);
    // if the variable exists in the current scope but its value is false, that means we
    // have declared it but not yet defined it. We report that error.
    if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
      Lox.error(expr.name, "Can't read local variable in its own initializer");
    }

    resolveLocal(expr, expr.name);
    return null;
  }

  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  private void resolve(Expr expr) {
    expr.accept(this);
  }

  private void resolveFunction(Stmt.Function function) {
    beginScope();
    for (Token param : function.params) {
      declare(param);
      define(param);
    }
    resolve(function.body);
    endScope();
  }

  private void beginScope() {
    scopes.push(new HashMap<String, Boolean>());
  }

  private void endScope() {
    scopes.pop();
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Boolean> scope = scopes.peek();

    // mark it as "not ready yet" by binding the name to false 
    // in the scope map
    scope.put(name.lexeme, false);
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    
    // fully initialized and available for use.
    scopes.peek().put(name.lexeme, true);
  }

  // helper to resolve a variable. If we walk through all the block scopes
  // and never find the variable, it's left unresolved and assumed to be
  // global.
  private void resolveLocal(Expr expr, Token name) {
    // Start a the innermost scope and work outwards, looking in 
    // each map for a matching name.
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        // resolve, passing in the number of scopes between the innermost scope
        // and the scope where the variable was found.
        // E.g., if the variable was found in the current scope, we pass in zero,
        // if it's in the immediately enclosing scope, 1.
        interpreter.resolve(expr, scopes.size() - 1 - i);
      }
    }
  }
}