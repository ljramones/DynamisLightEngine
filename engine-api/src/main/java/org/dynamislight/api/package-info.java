/**
 * Public host/runtime boundary for DynamicLightEngine.
 *
 * <p>Rules for this package:</p>
 * <ul>
 *   <li>DTOs are immutable and JavaFX-agnostic.</li>
 *   <li>No backend-native objects cross this boundary.</li>
 *   <li>Opaque IDs/handles are used for cross-boundary ownership.</li>
 *   <li>If a future DTO introduces {@code ByteBuffer}, ownership/copy semantics must be documented on that type.</li>
 * </ul>
 */
package org.dynamislight.api;
