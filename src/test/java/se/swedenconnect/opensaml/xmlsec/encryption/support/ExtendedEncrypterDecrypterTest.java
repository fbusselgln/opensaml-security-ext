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
package se.swedenconnect.opensaml.xmlsec.encryption.support;

import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.saml2.encryption.EncryptedElementTypeEncryptedKeyResolver;
import org.opensaml.saml.saml2.encryption.Encrypter.KeyPlacement;
import org.opensaml.security.x509.X509Credential;
import org.opensaml.xmlsec.DecryptionParameters;
import org.opensaml.xmlsec.encryption.EncryptedData;
import org.opensaml.xmlsec.encryption.support.ChainingEncryptedKeyResolver;
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.EncryptionConstants;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters;
import org.opensaml.xmlsec.encryption.support.SimpleKeyInfoReferenceEncryptedKeyResolver;
import org.opensaml.xmlsec.encryption.support.SimpleRetrievalMethodEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.impl.ChainingKeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.CollectionKeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.LocalKeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.provider.DEREncodedKeyValueProvider;
import org.opensaml.xmlsec.keyinfo.impl.provider.DSAKeyValueProvider;
import org.opensaml.xmlsec.keyinfo.impl.provider.InlineX509DataProvider;
import org.opensaml.xmlsec.keyinfo.impl.provider.RSAKeyValueProvider;
import org.opensaml.xmlsec.signature.DigestMethod;
import org.springframework.core.io.ClassPathResource;

import se.swedenconnect.opensaml.OpenSAMLTestBase;
import se.swedenconnect.opensaml.ecdh.security.x509.ECDHPeerCredential;
import se.swedenconnect.opensaml.xmlsec.encryption.ConcatKDFParams;
import se.swedenconnect.opensaml.xmlsec.encryption.ecdh.ECDHParameters;
import se.swedenconnect.opensaml.xmlsec.encryption.ecdh.EcEncryptionConstants;
import se.swedenconnect.opensaml.xmlsec.keyinfo.ECDHKeyInfoGeneratorFactory;
import se.swedenconnect.opensaml.xmlsec.keyinfo.provider.ECDHAgreementMethodKeyInfoProvider;

/**
 * Test cases for {@link ExtendedEncrypter} and {@link ExtendedDecrypter}.
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public class ExtendedEncrypterDecrypterTest extends OpenSAMLTestBase {
  
  @Test
  public void testEncryptDecryptManualSetup() throws Exception {
    
    final String ENCRYPTED_VALUE = "https://www.idsec.se";
    
    for (Provider p : Security.getProviders()) {
      System.out.println(p.getName());
    }
    
    X509Credential ecCredential = OpenSAMLTestBase.loadKeyStoreCredential(
      new ClassPathResource("eckey.jks").getInputStream(), "Test1234", "key1", "Test1234");
    
    X509Certificate ecCertificate = ecCredential.getEntityCertificate();
    
    // Object to encrypt (dummy)
    Issuer object = OpenSAMLTestBase.createXmlObject(Issuer.class, Issuer.DEFAULT_ELEMENT_NAME);
    object.setValue(ENCRYPTED_VALUE);
    
    // Set up parameters for encryption manually ...
    //
    DataEncryptionParameters dataEncryptionParameters = new DataEncryptionParameters();
    dataEncryptionParameters.setAlgorithm(EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES256_GCM);

    KeyEncryptionParameters kekParams = new KeyEncryptionParameters();
    kekParams.setAlgorithm(EcEncryptionConstants.ALGO_ID_KEYAGREEMENT_ECDH_ES);
    
    ECDHPeerCredential peerCredential = new ECDHPeerCredential(ecCertificate);
    ECDHParameters ecdhParameters = new ECDHParameters();
    ecdhParameters.setKeyWrapMethod(EncryptionConstants.ALGO_ID_KEYWRAP_AES256);
    peerCredential.setECDHParameters(ecdhParameters);
    ConcatKDFParams concatKDFParams = OpenSAMLTestBase.createXmlObject(ConcatKDFParams.class, ConcatKDFParams.DEFAULT_ELEMENT_NAME);
    DigestMethod digestMethod = OpenSAMLTestBase.createXmlObject(DigestMethod.class, DigestMethod.DEFAULT_ELEMENT_NAME);
    digestMethod.setAlgorithm(EncryptionConstants.ALGO_ID_DIGEST_SHA256);
    concatKDFParams.setDigestMethod(digestMethod);
    concatKDFParams.setAlgorithmID(Hex.decode("0000"));
    concatKDFParams.setPartyUInfo(Hex.decode("03d8"));
    concatKDFParams.setPartyVInfo(Hex.decode("03d0"));
    peerCredential.setConcatKDF(concatKDFParams);

    kekParams.setEncryptionCredential(peerCredential);
    
    ECDHKeyInfoGeneratorFactory ecdhFactory = new ECDHKeyInfoGeneratorFactory();
    ecdhFactory.setEmitX509IssuerSerial(true);
    ecdhFactory.setEmitPublicKeyValue(true);
    
    kekParams.setKeyInfoGenerator(ecdhFactory.newInstance());
    
    // Encrypt
    //
    ExtendedEncrypter encrypter = new ExtendedEncrypter(dataEncryptionParameters, kekParams);
    encrypter.setKeyPlacement(KeyPlacement.INLINE);

    EncryptedData encryptedData = encrypter.encryptElement(object, dataEncryptionParameters, kekParams);
    
    System.out.println(OpenSAMLTestBase.toString(encryptedData));
    
    // OK, let's try to decrypt ...
    //
    DecryptionParameters dparameters = new DecryptionParameters();
    
    // Setup the resolvers the way we normally would ...
    ChainingKeyInfoCredentialResolver kekKeyInfoCredentialResolver = new ChainingKeyInfoCredentialResolver(Arrays.asList(      
      new LocalKeyInfoCredentialResolver(Arrays.asList(
        new ECDHAgreementMethodKeyInfoProvider(Arrays.asList(ecCredential)),
        new RSAKeyValueProvider(), new DSAKeyValueProvider(), new DEREncodedKeyValueProvider(), new InlineX509DataProvider()),
        new CollectionKeyInfoCredentialResolver(Arrays.asList(ecCredential))),
      new StaticKeyInfoCredentialResolver(Arrays.asList(ecCredential))));
    
    dparameters.setKEKKeyInfoCredentialResolver(kekKeyInfoCredentialResolver);

    ChainingEncryptedKeyResolver encryptedKeyResolver = new ChainingEncryptedKeyResolver(Arrays.asList(
      new InlineEncryptedKeyResolver(), new EncryptedElementTypeEncryptedKeyResolver(),
      new SimpleRetrievalMethodEncryptedKeyResolver(), new SimpleKeyInfoReferenceEncryptedKeyResolver()));

    dparameters.setEncryptedKeyResolver(encryptedKeyResolver);
        
    Decrypter decrypter = new Decrypter(dparameters);
    decrypter.setRootInNewDocument(true);
    
    XMLObject decryptedObject = decrypter.decryptData(encryptedData);
    System.out.println(OpenSAMLTestBase.toString(decryptedObject));
  }

}
