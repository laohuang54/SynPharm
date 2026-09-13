---
license: cc-by-nc-4.0
---


# FlashPPI: Linear-time prediction of proteome-scale microbial protein interactions


FlashPPI is a contrastively trained model for protein-protein interaction (PPI) prediction, grounded in residue-level interactions, that enables full-proteome interaction prediction in minutes.

By reframing PPI prediction as a dense retrieval task, FlashPPI circumvents the quadratic computational bottleneck of traditional all-vs-all structural screening.

- **Scalable:** Reduces proteome-wide screening from days/months to minutes.
- **Interpretable:** Predicts fine-grained, residue-level 2D contact maps for retrieved interaction candidates.
- **Genomic Priors:** Leverages [gLM2](https://huggingface.co/tattabio/gLM2_650M) to capture cross-protein, multi-gene co-evolutionary signals.


## Web Server
FlashPPI is integrated into [seqhub.org](https://seqhub.org). You can upload a FASTA and interactively explore whole-proteome networks and contact maps. Explore an example network [here](https://seqhub.org/tattabio/mycobacterium_tb?ppi=true).


## Usage

See the github repo https://github.com/TattaBio/flashppi for usage and inference scripts.

## License
The model code and inference scripts in the GitHub repo are licensed under the Apache License 2.0.

The FlashPPI model weights are hosted on Hugging Face and released under the Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0) license. The weights are freely available for academic and research purposes.

## Citing 
If you use FlashPPI or our datasets in your research, please cite:

```
@article{
  doi:10.1073/pnas.2610619123,
  author = {Andre Cornman  and Matt Tranzillo  and Nicolo G. Zulaybar  and Imane Bouzit  and Yunha Hwang },
  title = {Linear-time prediction of proteome-scale microbial protein interactions},
  journal = {Proceedings of the National Academy of Sciences},
  volume = {123},
  number = {25},
  pages = {e2610619123},
  year = {2026},
  doi = {10.1073/pnas.2610619123},
  URL = {https://www.pnas.org/doi/abs/10.1073/pnas.2610619123},
  eprint = {https://www.pnas.org/doi/pdf/10.1073/pnas.2610619123},
}
```