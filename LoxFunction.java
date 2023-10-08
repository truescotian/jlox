package jlox;

import java.util.List;

import jlox.Environment;

class LoxFunction implements LoxCallable {
  private final Stmt.Function declaration;
  private final Environment closure;

  private final boolean isInitializer;

  LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
    this.isInitializer = isInitializer;
    this.closure = closure;
    this.declaration = declaration;
  }

  // hook up LoxFunction to a LoxInstance, AKA make it a method.
  // when there's a function in a class (a method), bind the function to the class instance (LoxInstance)
  LoxFunction bind(LoxInstance instance) {
    // create a new env nestled inside the method's original closure
    Environment environment = new Environment(closure);
    // declare "this" as a variable in environment and bind it to the given instance
    environment.define("this", instance);
    return new LoxFunction(declaration, environment, isInitializer);
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    Environment environment = new Environment(closure);
    for (int i = 0; i < declaration.params.size(); i++) {
      environment.define(declaration.params.get(i).lexeme, arguments.get(i));
    }

    try {
      interpreter.executeBlock(declaration.body, environment);
    } catch (Return returnValue) {
      if (isInitializer) return closure.getAt(0, "this"); // allow using "return;" in init func
      return returnValue.value;
    }

    // when func is an initializer (init) force returning this
    if (isInitializer) return closure.getAt(0, "this");

    return null;
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  @Override 
  public String toString() {
    return "<fn " + declaration.name.lexeme + ">";
  }
}