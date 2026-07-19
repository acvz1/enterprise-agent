# Module and Project Reading

Use this reference only for module or project-level requests.

## Project Map

Identify only what is needed to establish the main runtime flow:

```text
entry point
configuration
request or command boundary
business service
persistence or external service
response/output
```

Give each layer one responsibility and one non-responsibility. Follow one representative request end to end before listing secondary modules.

## Module Summary

Include:

```text
module requirement
responsibility and non-responsibility
important files
upstream input
downstream output
main call chain
state or core data structure
one important failure boundary
```

Select the few methods that express business decisions. Skip repetitive CRUD, getters/setters, imports, and equivalent methods unless the learner asks for them.

## Chapter or Milestone Summary

Build the summary in this order:

```text
first-principles problem
architecture and responsibility boundaries
complete end-to-end flow
core data structures and state ownership
necessary code paths
verified tradeoff or failure boundary
compact mental model
```

Do not write a milestone summary while the learner is still confused about its central call chain. Resolve that gap first.

## Reading Direction

Use outer flow before inner implementation at a new boundary:

- Web/API: browser action -> Controller -> Service -> Mapper -> database -> response.
- CLI: command registration -> dispatch -> input -> state -> output.
- Agent: user message -> context -> model -> tool -> observation -> history -> answer.

Once the flow is mapped, answer local questions locally instead of replaying the whole architecture.
