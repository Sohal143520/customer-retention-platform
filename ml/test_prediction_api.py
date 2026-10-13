import unittest

from prediction_api import CustomerFeatures, predict, prediction_result


class PredictionExplanationTests(unittest.TestCase):
    def test_bulk_style_prediction_result_does_not_include_explanation(self):
        self.assertNotIn("model_explanation", prediction_result(0.64))

    def test_single_prediction_includes_ranked_non_causal_model_contributions(self):
        customer = CustomerFeatures(
            age_group="35-44",
            region="North",
            customer_segment="Premium",
            tenure_months=8,
            monthly_spend_inr=4200,
            transactions_90d=5,
            support_tickets_90d=4,
            avg_response_hours=18.0,
            satisfaction_score=2,
            app_logins_30d=3,
            days_since_last_purchase=75,
            discount_usage_pct=65,
            payment_failures_90d=2,
        )

        result = predict(customer)
        explanation = result["model_explanation"]

        self.assertIn("Logistic Regression", explanation["method"])
        self.assertIn("not proof", explanation["note"])
        self.assertTrue(explanation["risk_increasing"])
        self.assertTrue(explanation["risk_reducing"])
        self.assertLessEqual(len(explanation["risk_increasing"]), 3)
        self.assertLessEqual(len(explanation["risk_reducing"]), 3)
        self.assertTrue(all(
            item["log_odds_contribution"] > 0
            for item in explanation["risk_increasing"]
        ))
        self.assertTrue(all(
            item["log_odds_contribution"] < 0
            for item in explanation["risk_reducing"]
        ))


if __name__ == "__main__":
    unittest.main()
