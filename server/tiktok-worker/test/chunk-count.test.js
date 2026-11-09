import test from "node:test";
import assert from "node:assert/strict";
import { expectedChunkCount } from "./src/index.js";

const MB = 1024 * 1024;

test("small videos use one chunk", () => {
  assert.equal(expectedChunkCount(2 * MB, 2 * MB), 1);
});

test("exact chunk multiples do not create an empty final chunk", () => {
  assert.equal(expectedChunkCount(10 * MB, 5 * MB), 2);
  assert.equal(expectedChunkCount(20 * MB, 10 * MB), 2);
});

test("partial final chunks are counted", () => {
  assert.equal(expectedChunkCount(7 * MB, 5 * MB), 2);
  assert.equal(expectedChunkCount(25 * MB, 10 * MB), 3);
  assert.equal(expectedChunkCount(5 * MB + 1, 5 * MB), 2);
});

test("invalid sizes are rejected", () => {
  assert.throws(() => expectedChunkCount(0, MB));
  assert.throws(() => expectedChunkCount(MB, 0));
  assert.throws(() => expectedChunkCount(Number.MAX_SAFE_INTEGER + 1, MB));
});
