/*
 * Copyright 2019 Sweden Connect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.swedenconnect.opensaml.xmlsec.algorithm;

import javax.annotation.Nullable;

import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.algorithm.AlgorithmDescriptor;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;

/**
 * OpenSAML's {@link AlgorithmSupport} class does not offer utility methods needed for key agreement. This class offers
 * such methods.
 * 
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public final class ExtendedAlgorithmSupport {
  
  /**
   * Checks whether the supplied descriptor represents an algorithm that my be used for key wrapping.
   * 
   * @param algorithm
   *          the algorithm descriptor to evaluate
   * @return {@code true} if the algorithm may be used for key wrapping, {@code false} otherwise
   */
  public static boolean isKeyWrappingAlgorithm(@Nullable final AlgorithmDescriptor algorithm) {
    if (algorithm == null) {
      return false;
    }
    return AlgorithmDescriptor.AlgorithmType.SymmetricKeyWrap.equals(algorithm.getType());
  }

  /**
   * Check whether the supplied peer credential may be used in a key agreement protocol.
   * 
   * @param credential
   *          the credential to test
   * @return {@code true} if the credential may be used in a key agreement protocol, and {@code false} otherwise
   */
  public static boolean peerCredentialSupportsKeyAgreement(@Nullable final Credential credential) {
    if (credential == null) {
      return false;
    }
    if (credential.getPublicKey() != null && credential.getPublicKey().getAlgorithm().equals("EC")) {
      // Currently, we only support EC keys in key agreement protocols ...
      return true;
    }
    return false;
  }

  // Hidden constructor.
  private ExtendedAlgorithmSupport() {
  }

}
