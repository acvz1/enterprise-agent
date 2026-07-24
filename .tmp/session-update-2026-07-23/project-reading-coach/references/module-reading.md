# Module and Project Reading

Use this reference only for module, project, architecture, roadmap, or interview-ownership requests.

## Ownership Boundary

The learner owns the project goal, product identity, target role, deadline, and final roadmap.

Do not pivot the product, invent a new scenario, or choose a large feature because it sounds more agentic or impressive. Present tradeoffs and connect them to source evidence; let the learner decide.

## Project Map

Identify only what establishes one representative runtime flow:

~~~text
entry point
configuration
request or command boundary
business service
persistence or external service
response/output
~~~

Give each layer one responsibility and one non-responsibility. Follow one request end to end before listing secondary modules.

## Module Summary

Include:

~~~text
module requirement
responsibility and non-responsibility
important files
upstream input
downstream output
main call chain
state or core data structure
one important failure boundary
~~~

Select methods that express business decisions. Skip repetitive CRUD, getters/setters, imports, and equivalent methods unless requested.

## Reading-to-Writing Boundary

Do not treat reading every file as a prerequisite for implementation.

Before reading another module, ask privately:

~~~text
Does this file block the next authorized code change?
Will it provide a call-chain fact needed for implementation or interview explanation?
Can the behavior be learned faster through one runnable experiment?
~~~

If all answers are no, stop reading.

When the user switches to implementation, preserve the latest source cursor but leave reading mode immediately.

## Roadmap

Connect each proposed functional unit to:

~~~text
user-visible capability
exact code area
verification
resume/interview evidence
estimated effort
dependency
~~~

Estimate by functional unit unless a calendar is explicitly requested. A plan is not complete until each unit has an implementation and verification path.

Do not describe planned features as project strengths. Only implemented and verified behavior may become a resume claim.

## Chapter or Milestone Summary

Build the summary in this order:

~~~text
first-principles problem
architecture and responsibility boundaries
complete end-to-end flow
core data structures and state ownership
necessary code paths
verified tradeoff or failure boundary
compact mental model
~~~

Do not write a milestone summary while the learner remains confused about its central call chain.

## Reading Direction

Use outer flow before inner implementation at a new boundary:

- Web/API: browser action -> Controller -> Service -> Mapper/Repository -> database -> response.
- CLI: command registration -> dispatch -> input -> state -> output.
- Agent: user message -> context -> model decision -> tool -> observation -> state/history -> next decision or final answer.

Once the flow is mapped, answer local questions locally instead of replaying the architecture.
