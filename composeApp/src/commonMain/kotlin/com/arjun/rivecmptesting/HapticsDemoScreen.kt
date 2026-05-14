package com.arjun.rivecmptesting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.arjun.rivecmptesting.haptics.Haptics
import com.arjun.rivecmptesting.haptics.PrimitiveType

@Composable
fun HapticsDemoScreen() {

    val haptics =
        LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {

            Text(
                text = "Premium KMP Haptics",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // ---------------------------------------------------
        // Splash
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    Haptics.splash()
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Splash\n" +
                                "PRIMITIVE_SLOW_RISE + PRIMITIVE_QUICK_FALL"
                )
            }
        }

        // ---------------------------------------------------
        // Primary Button
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

//                    haptics.performHapticFeedback(
//                        HapticFeedbackType.LongPress
//                    )

                    Haptics.primaryButton()
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Primary Button\n" +
                                "PRIMITIVE_CLICK"
                )
            }
        }

        // ---------------------------------------------------
        // Gem Credit
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    Haptics.gemCredit()
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Gem Credit\n" +
                                "PRIMITIVE_SLOW_RISE + PRIMITIVE_CLICK"
                )
            }
        }

        // ---------------------------------------------------
        // Payment Success
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    Haptics.paymentSuccess()
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Payment Success\n" +
                                "PRIMITIVE_SLOW_RISE + " +
                                "PRIMITIVE_QUICK_FALL + " +
                                "PRIMITIVE_CLICK"
                )
            }
        }

        // ---------------------------------------------------
        // Match Making
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    Haptics.matchMaking()
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Match Making\n" +
                                "PRIMITIVE_SLOW_RISE + PRIMITIVE_SPIN"
                )
            }
        }

        // ---------------------------------------------------
        // Chest Open
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    Haptics.chestOpen()
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Chest Open\n" +
                                "PRIMITIVE_TICK → " +
                                "PRIMITIVE_CLICK → " +
                                "PRIMITIVE_THUD"
                )
            }
        }

        // ---------------------------------------------------
        // Semantic - Popup
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    haptics.performHapticFeedback(
                        HapticFeedbackType.VirtualKey
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Popup\n" +
                                "Semantic: VirtualKey"
                )
            }
        }

        // ---------------------------------------------------
        // Semantic - Bottom Nav
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    haptics.performHapticFeedback(
                        HapticFeedbackType.KeyboardTap
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Bottom Nav\n" +
                                "Semantic: KeyboardTap"
                )
            }
        }

        // ---------------------------------------------------
        // Semantic - Reject
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    haptics.performHapticFeedback(
                        HapticFeedbackType.Reject
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Payment Fail\n" +
                                "Semantic: Reject"
                )
            }
        }

        // ---------------------------------------------------
        // DSL Example
        // ---------------------------------------------------

        item {

            Button(
                onClick = {

                    Haptics.custom {

                        primitive(
                            type = PrimitiveType.SLOW_RISE
                        )

                        primitive(
                            type = PrimitiveType.CLICK
                        )

                        primitive(
                            type = PrimitiveType.THUD,
                        )

                        primitive(
                            type = PrimitiveType.QUICK_FALL,
                        )

                        primitive(
                            type = PrimitiveType.SPIN,
                        )

                        primitive(
                            type = PrimitiveType.TICK,
                        )

                        primitive(
                            type = PrimitiveType.SLOW_RISE
                        )

                        primitive(
                            type = PrimitiveType.CLICK
                        )

                        primitive(
                            type = PrimitiveType.THUD,
                        )

                        primitive(
                            type = PrimitiveType.QUICK_FALL,
                        )

                        primitive(
                            type = PrimitiveType.SPIN,
                        )

                        primitive(
                            type = PrimitiveType.TICK,
                        )

                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Custom DSL\n" +
                                "Primitive Composition Builder"
                )
            }
        }

        // ---------------------------------------------------
        // Cancel
        // ---------------------------------------------------

        item {

            OutlinedButton(
                onClick = {

                    Haptics.cancel()
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Cancel Haptics")
            }
        }
    }
}