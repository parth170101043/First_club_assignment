Always add comment to the code.

a) The comment should conatin the objective of the file and each method.
b) Each method comment should have objective, parameter description, return type
and who uses this method.
c) For python, the comment should follows the sphinx style.
d) and for other it should follow industry standard docs.
e) While writing comment for a method param, class member or a variable, if it has a
default value mention it.
f) Don't mention Day1, 2... in comments
g) write the comments with discipline and readability
h) For java always use 
/**
 *   text..
*/
format for comment.
i) always explain paramter list.

for example: 
incorrect: 
:param model: The Gemini model used by the chatbot node. If ``None``, the graph can still
        be compiled for diagram generation, but it cannot answer messages.
:type model: Optional[ChatGoogleGenerativeAI]
:return: A compiled LangGraph app without checkpointing attached.
:rtype: langgraph.graph.state.CompiledStateGraph

Correct:
:param model: The Gemini model used by the chatbot node. If ``None``, the graph can still
               be compiled for diagram generation, but it cannot answer messages.
:type model:  Optional[ChatGoogleGenerativeAI]
:return:      A compiled LangGraph app without checkpointing attached.
:rtype:       langgraph.graph.state.CompiledStateGraph
h) similarly maintain discipline and readability with code also

Create a file Impl.md (if not exists), each time file modified or created, append in that file
timestamp: