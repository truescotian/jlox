package jlox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();
  private FunctionType currentFunction = FunctionType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
  }

  private enum ClassType {
    NONE,
    CLASS,
    SUBCLASS
  }

  // tells us if we're currently inside a class declaration
  // while traversing the syntax tree. It starts out as NONE
  // meaning we aren't in one.
  private ClassType currentClass = ClassType.NONE;

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
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass; // restored at end of func
    currentClass = ClassType.CLASS;

    declare(stmt.name);
    define(stmt.name);

    if (stmt.superclass != null) {
      currentClass = ClassType.SUBCLASS;
      if (stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
        Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
      }
      resolve(stmt.superclass);
    }

    if (stmt.superclass != null) {
      // create the superclass environment
      beginScope();
      scopes.peek().put("super", true);
    }

    beginScope();

    // whenever "this" is encountered, it will resolve to a 
    // "local variable" defined in an implicit scope
    // OUTSIDE of the block for the method body
    scopes.peek().put("this", true);

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }
      resolveFunction(method, declaration); 
    }

    endScope();

    if (stmt.superclass != null) endScope();

    currentClass = enclosingClass; // restore old value
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt, FunctionType.FUNCTION);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.");
    }
    
    if (stmt.value != null) {
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword, "Can't return a value from an initializer.");
      }
      resolve(stmt.value);
    }

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
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
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
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Can't use 'super' outside of a class.");
    } else if (currentClass != ClassType.SUBCLASS) {
      Lox.error(expr.keyword,
          "Can't use 'super' in a class with no superclass.");
    }
    
    resolveLocal(expr, expr.keyword);
    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Can't use 'this' outside of a class.");
          return null;
    }
    resolveLocal(expr, expr.keyword);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
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

  private void resolveFunction(Stmt.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();
    for (Token param : function.params) {
      declare(param);
      define(param);
    }
    resolve(function.body);
    endScope();
    currentFunction = enclosingFunction;
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

    if (scope.containsKey(name.lexeme)) {
      Lox.error(name,
          "Already a variable with this name in this scope.");
    }

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