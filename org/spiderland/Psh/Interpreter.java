
package org.spiderland.Psh;

import java.util.*;
import java.lang.*;

import org.spiderland.Psh.*;

/**
 * The Push language interpreter.
 */

public class Interpreter {
    HashMap< String, Instruction > _instructions = new HashMap< String, Instruction >();

    // All generators

    HashMap< String, AtomGenerator > _generators = new HashMap< String, AtomGenerator >();
    ArrayList< AtomGenerator > _randomGenerators = new ArrayList< AtomGenerator >();

    intStack		_intStack;
    floatStack		_floatStack;
    booleanStack	_boolStack;
    ObjectStack		_codeStack;
    ObjectStack		_nameStack;
    ObjectStack		_execStack = new ObjectStack();

    ObjectStack		_intFrameStack = new ObjectStack();
    ObjectStack		_floatFrameStack = new ObjectStack();
    ObjectStack		_boolFrameStack = new ObjectStack();
    ObjectStack		_codeFrameStack = new ObjectStack();
    ObjectStack		_nameFrameStack = new ObjectStack();

    boolean		_useFrames;

    int			_effort;

    int			_maxRandomInt;
    int			_minRandomInt;
    int			_randomIntResolution;

    float		_maxRandomFloat;
    float		_minRandomFloat;
    float		_randomFloatResolution;

    Random		_RNG = new Random();

    public Interpreter() {
	_maxRandomInt = 10;
	_minRandomInt = 0;
	_randomIntResolution = 1;

	_maxRandomFloat = 10.0f;
	_minRandomFloat = 0.0f;
	_randomFloatResolution = .5f;
	_useFrames = false;

	PushStacks();

	DefineInstruction( "INTEGER.+", new IntegerAdd() );
	DefineInstruction( "INTEGER.-", new IntegerSub() );
	DefineInstruction( "INTEGER./", new IntegerDiv() );
	DefineInstruction( "INTEGER.%", new IntegerMod() );
	DefineInstruction( "INTEGER.*", new IntegerMul() );
	DefineInstruction( "INTEGER.=", new IntegerEquals() );
	DefineInstruction( "INTEGER.>", new IntegerGreaterThan() );
	DefineInstruction( "INTEGER.<", new IntegerLessThan() );

	DefineInstruction( "FLOAT.+", new FloatAdd() );
	DefineInstruction( "FLOAT.-", new FloatSub() );
	DefineInstruction( "FLOAT./", new FloatDiv() );
	DefineInstruction( "FLOAT.%", new FloatMod() );
	DefineInstruction( "FLOAT.*", new FloatMul() );
	DefineInstruction( "FLOAT.=", new FloatEquals() );
	DefineInstruction( "FLOAT.>", new FloatGreaterThan() );
	DefineInstruction( "FLOAT.<", new FloatLessThan() );

	DefineInstruction( "CODE.QUOTE", new Quote() );

	DefineInstruction( "EXEC.DO*TIMES", new ExecDoTimes(this) );
	DefineInstruction( "CODE.DO*TIMES", new CodeDoTimes(this) );
	DefineInstruction( "EXEC.DO*COUNT", new ExecDoCount(this) );
	DefineInstruction( "CODE.DO*COUNT", new CodeDoCount(this) );
	DefineInstruction( "EXEC.DO*RANGE", new ExecDoRange(this) );
	DefineInstruction( "CODE.DO*RANGE", new CodeDoRange(this) );
	DefineInstruction( "CODE.=", new ObjectEquals( _codeStack ) );
	DefineInstruction( "EXEC.=", new ObjectEquals( _execStack ) );
	DefineInstruction( "CODE.IF", new If( _codeStack ) );
	DefineInstruction( "EXEC.IF", new If( _execStack ) );

	DefineInstruction( "TRUE", new BooleanConstant( true ) );
	DefineInstruction( "FALSE", new BooleanConstant( false ) );

	DefineStackInstructions( "INTEGER", _intStack );
	DefineStackInstructions( "FLOAT", _floatStack );
	DefineStackInstructions( "BOOLEAN", _boolStack );
	DefineStackInstructions( "NAME", _nameStack );
	DefineStackInstructions( "CODE", _codeStack );
	DefineStackInstructions( "EXEC", _execStack );

	DefineInstruction( "FRAME.PUSH", new PushFrame() );
	DefineInstruction( "FRAME.POP", new PopFrame() );

	_generators.put( "FLOAT.ERC", new FloatAtomGenerator() );
	_generators.put( "INTEGER.ERC", new IntAtomGenerator() );
    }

    /**
     * Enables experimental Push "frames" 
     *
     * When frames are enabled, each Push subtree is given a fresh set of stacks (a "frame")
     * when it executes.  When a frame is pushed, the top value from each stack is passed to
     * the new frame, and likewise when the frame pops, allowing for input arguments and 
     * return values.
     */

    public void SetUseFrames( boolean inUseFrames ) {
	_useFrames = inUseFrames;
    }

    /**
     * Defines the instruction set used for random code generation in this Push interpreter.
     * @param inInstructionList A program consisting of a list of string instruction names to 
     * 			    be placed in the instruction set.
     */

    public void SetInstructions( Program inInstructionList ) throws RuntimeException {
	_randomGenerators.clear();

	for( int n = 0; n < inInstructionList.size(); n++ ) {
	    Object o = inInstructionList.peek( n );

	    if( ! ( o instanceof String ) ) 
		throw new RuntimeException( "Instruction list must contain a list of Push instruction names only" );

	    String name = (String)o;

	    //Check for REGISTERED
	    if(name.indexOf("REGISTERED.") == 0){
		String registeredType = name.substring(11);

		if(!registeredType.equals("INTEGER") && 
		   !registeredType.equals("FLOAT") &&
		   !registeredType.equals("BOOLEAN") &&
		   !registeredType.equals("EXEC") &&
		   !registeredType.equals("CODE") &&
		   !registeredType.equals("NAME") &&
		   !registeredType.equals("FRAME")){
		    System.out.println( "Unknown instruction \"" + name + "\" in instruction set" );
		}
		else{
		    //Legal stack type, so add all generators matching
		    //registeredType to _randomGenerators.
		    Object keys[] = _instructions.keySet().toArray();
		    
		    for(int i = 0; i < keys.length; i++){
			String key = (String)keys[i];
			if(key.indexOf(registeredType) == 0){
			    AtomGenerator g = _generators.get(key);
			    _randomGenerators.add(g);
			}
		    }

		    if(registeredType.equals("BOOLEAN")){
			AtomGenerator t = _generators.get("TRUE");
			_randomGenerators.add(t);
			AtomGenerator f = _generators.get("FALSE");
			_randomGenerators.add(f);
		    }

		}
	    }
	    else{
		AtomGenerator g = _generators.get( name );
		
		if( g == null ) {
		    System.out.println( "Unknown instruction \"" + name + "\" in instruction set" );
		} else {
		    _randomGenerators.add( g );
		}
	    }
	}
    }

    public void AddInstruction( String inName, Instruction inInstruction ) {
	DefineInstruction( inName, inInstruction );
	_randomGenerators.add( new InstructionAtomGenerator( inName ) );
    }

    private void DefineInstruction( String inName, Instruction inInstruction ) {
	_instructions.put( inName, inInstruction );
	_generators.put( inName, new InstructionAtomGenerator( inName ) );
    }

    private void DefineStackInstructions( String inTypeName, Stack inStack ) {
	DefineInstruction( inTypeName + ".POP", new Pop( inStack ) );
	DefineInstruction( inTypeName + ".SWAP", new Swap( inStack ) );
	DefineInstruction( inTypeName + ".ROT", new Rot( inStack ) );
	DefineInstruction( inTypeName + ".FLUSH", new Flush( inStack ) );
	DefineInstruction( inTypeName + ".DUP", new Dup( inStack ) );
	DefineInstruction( inTypeName + ".STACKDEPTH", new Depth( inStack ) );
    }

    /**
     * Executes a Push program with no execution limit.
     *
     * @return The number of instructions executed.
     */

    public int Execute( Program inProgram ) {
	return Execute( inProgram, -1 );
    }

    /**
     * Executes a Push program with a given instruction limit.
     *
     * @param inMaxSteps The maximum number of instructions allowed to be executed.
     * @return The number of instructions executed.
     */

    public int Execute( Program inProgram, int inMaxSteps ) {
	_codeStack.push( inProgram );
	LoadProgram( inProgram ); // Initiallizes program
	return Step( inMaxSteps );
    }

    /**
     * Loads a Push program into the interpreter's exec stack.
     *
     * @param inProgram The program to load.
     */

    public void LoadProgram(Program inProgram){
	_execStack.push(inProgram);
    }

    /**
     * Steps a Push interpreter forward with a given instruction limit.
     * 
     * This method assumes that the intepreter is already setup with an
     * active program (typically using \ref Execute). 
     *
     * @param inMaxSteps The maximum number of instructions allowed to be executed.
     * @return The number of instructions executed.
     */

    public int Step( int inMaxSteps ) {
	int executed = 0;
	while( inMaxSteps != 0 && _execStack.size() > 0 ) {
	    ExecuteInstruction( _execStack.pop() );
	    inMaxSteps--;
	    executed++;
	}

	_effort += executed;

	return executed;
    }

    public int ExecuteInstruction( Object inObject ) {
	Class objectClass = inObject.getClass();

	if( inObject instanceof Program ) {
	    Program p = (Program)inObject;

	    if(_useFrames) {
		_execStack.push( "FRAME.POP" );
	    }

	    p.PushAllReverse( _execStack );

	    if( _useFrames ) {
		_execStack.push( "FRAME.PUSH" );
	    }

	    return 0;
	}
		
	if( inObject instanceof Integer ) {
	    _intStack.push( (Integer)inObject );
	    return 0;
	}

	if( inObject instanceof Number ) {
	    _floatStack.push( ( (Number)inObject ).floatValue() );
	    return 0;
	}

	if( inObject instanceof String ) {
	    Instruction i = _instructions.get( (String)inObject );

	    if( i != null ) {
		i.Execute( this );
	    } else {
		_nameStack.push( (String)inObject );
	    }

	    return 0;
	}

	return -1;
    }

    /**
     * Fetch the active integer stack. 
     */

    public intStack intStack() {
	return _intStack;
    }

    /**
     * Fetch the active float stack. 
     */

    public floatStack floatStack() {
	return _floatStack;
    }

    /**
     * Fetch the active exec stack. 
     */

    public ObjectStack execStack() {
	return _execStack;
    }

    /**
     * Fetch the active code stack. 
     */

    public ObjectStack codeStack() {
	return _codeStack;
    }

    /**
     * Fetch the active bool stack. 
     */

    public booleanStack boolStack() {
	return _boolStack;
    }

    /**
     * Fetch the active name stack. 
     */

    public ObjectStack nameStack() {
	return _nameStack;
    }


    private void AssignStacksFromFrame() {
	_floatStack = (floatStack)_floatFrameStack.top();
	_intStack   = (intStack)_intFrameStack.top();
	_boolStack  = (booleanStack)_boolFrameStack.top();
	_codeStack  = (ObjectStack)_codeFrameStack.top();
	_nameStack  = (ObjectStack)_nameFrameStack.top();
    }

    public void PushStacks() {
	_floatFrameStack.push( new floatStack() );
	_intFrameStack.push( new intStack() );
	_boolFrameStack.push( new booleanStack() );
	_codeFrameStack.push( new ObjectStack() );
	_nameFrameStack.push( new ObjectStack() );

	AssignStacksFromFrame();
    }

    public void PopStacks() {
	_floatFrameStack.pop();
	_intFrameStack.pop();
	_boolFrameStack.pop();
	_codeFrameStack.pop();
	_nameFrameStack.pop();

	AssignStacksFromFrame();
    }

    public void PushFrame() {
	if( _useFrames ) {
	    boolean boolTop = _boolStack.top();
	    int intTop      = _intStack.top();
	    float  floatTop = _floatStack.top();
	    Object nameTop  = _nameStack.top();
	    Object codeTop  = _codeStack.top();
	
	    PushStacks();
	
	    _floatStack.push( floatTop );
	    _intStack.push( intTop );
	    _boolStack.push( boolTop );
	
	    if( nameTop != null )
		_nameStack.push( nameTop );
	    if( codeTop != null )
		_codeStack.push( codeTop );
	}
    }

    public void PopFrame() {
	if( _useFrames ) {
	    boolean boolTop = _boolStack.top();
	    int intTop      = _intStack.top();
	    float  floatTop = _floatStack.top();
	    Object nameTop  = _nameStack.top();
	    Object codeTop  = _codeStack.top();
	
	    PopStacks();
	
	    _floatStack.push( floatTop );
	    _intStack.push( intTop );
	    _boolStack.push( boolTop );
	
	    if( nameTop != null )
		_nameStack.push( nameTop );
	    if( codeTop != null )
		_codeStack.push( codeTop );
	}
    }

    /**
     * Prints out the current stack states.
     */

    public void PrintStacks() {
	System.out.println( this );
    }

    /**
     * Returns a string containing the current Interpreter stack states.
     */

    public String toString() {
	String result = "";
	result += "exec stack: "    + _execStack + "\n";
	result += "code stack: "    + _codeStack + "\n";
	result += "int stack: "     + _intStack + "\n";
	result += "float stack: "   + _floatStack + "\n";
	result += "boolean stack: " + _boolStack + "\n";
	result += "name stack: "    + _nameStack + "\n";

	return result;
    }

    /**
     * Resets the Push interpreter state by clearing all of the stacks.
     */

    public void ClearStacks() {
	_intStack.clear();
	_floatStack.clear();
	_execStack.clear();
	_nameStack.clear();
	_boolStack.clear();
	_codeStack.clear();
    }

    /**
     * Returns a string list of all instructions enabled in the interpreter.
     */

    public String GetInstructionString() {
	Object keys[] = _instructions.keySet().toArray();
	String list = "";
		
	for( int n = 0; n < keys.length; n++ ) 
	    list += keys[ n ] + " ";

	return list;
    }

    /**
     * Generates a single random Push atom (instruction name, integer, float, etc) for 
     * use in random code generation algorithms.
     * 
     * @return A random atom based on the interpreter's current active instruction set.
     */

    public Object RandomAtom() {
	int index = _RNG.nextInt( _randomGenerators.size() );

	return _randomGenerators.get( index ).Generate( this );
    }

    /**
     * Generates a random Push program of a given size.
     * 
     * @param inSize The requested size for the program to be generated.
     * @return A random Push program of the given size.
     */

    public Program RandomCode( int inSize ) {
	Program p = new Program();

	List< Integer > distribution = RandomCodeDistribution( inSize - 1, inSize - 1 );

	for( int i = 0; i < distribution.size(); i++ ) {
	    int count = distribution.get( i );

	    if( count == 1 ) {
		p.push( RandomAtom() );
	    } else {
		p.push( RandomCode( count ) );
	    }
	}

	return p;
    }

    /**
     * Generates a list specifying a size distribution to be used for random code.  
     *
     * Note: This method is called "decompose" in the lisp implementation.
     * 
     * @param inCount       The desired resulting program size.
     * @param inMaxElements The maxmimum number of elements at this level.
     * @return 		A list of integers representing the size distribution.
     */

    public List< Integer > RandomCodeDistribution( int inCount, int inMaxElements ) {
	ArrayList< Integer > result = new ArrayList< Integer >();

	RandomCodeDistribution( result, inCount, inMaxElements );

	Collections.shuffle( result );

	return result;
    }

    /**
     * The recursive worker function for the public RandomCodeDistribution.
     * 
     * @param ioList        The working list of distribution values to append to.
     * @param inCount       The desired resulting program size.
     * @param inMaxElements The maxmimum number of elements at this level.
     */

    private void RandomCodeDistribution( List< Integer > ioList, int inCount, int inMaxElements ) {
	if( inCount < 1 )
	    return;

	int thisSize = inCount < 2 ? 1 : ( _RNG.nextInt( inCount ) + 1 );

	ioList.add( thisSize );

	RandomCodeDistribution( ioList, inCount - thisSize, inMaxElements - 1 );
    }

    abstract class AtomGenerator {
	abstract Object Generate( Interpreter inInterpreter );
    }

    private class InstructionAtomGenerator extends AtomGenerator {
	InstructionAtomGenerator( String inInstructionName ) {
	    _instruction = inInstructionName;
	}

	Object Generate( Interpreter inInterpreter ) {
	    return _instruction;
	}

	String _instruction;
    }

    private class FloatAtomGenerator extends AtomGenerator {
	Object Generate( Interpreter inInterpreter ) {
	    float r = _RNG.nextFloat() * ( _maxRandomFloat - _minRandomFloat );

	    r -= ( r % _randomFloatResolution );

	    return r + _minRandomFloat;
	}
    }

    private class IntAtomGenerator extends AtomGenerator {
	Object Generate( Interpreter inInterpreter ) {
	    int r = _RNG.nextInt( _maxRandomInt - _minRandomInt );
			
	    r -= ( r % _randomIntResolution );

	    return r + _minRandomInt;
	}
    }
}
